package org.dromara.reader.service.impl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.vo.app.AppPointsRewardVo;
import org.dromara.reader.domain.vo.app.AppPointsTaskVo;
import org.dromara.reader.domain.vo.app.AppPointsVo;
import org.dromara.reader.service.IReaderPointsService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.service.cache.ReaderRedisCacheClient;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 阅读积分服务实现，负责积分中心、签到与任务积分的状态管理。
 */
@Service
@RequiredArgsConstructor
public class ReaderPointsServiceImpl implements IReaderPointsService {

    /**
     * 积分任务默认缓存前缀。
     */
    private static final String POINTS_STATE_PREFIX = "reader:points:state:";

    /**
     * 默认累计积分。
     */
    private static final int DEFAULT_TOTAL_POINTS = 3280;

    /**
     * 默认今日积分。
     */
    private static final int DEFAULT_TODAY_POINTS = 36;

    /**
     * 默认连续签到天数。
     */
    private static final int DEFAULT_STREAK_DAYS = 15;

    /**
     * 访客/登录读者解析服务。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * Redis 封装。
     */
    private final ReaderRedisCacheClient redisCacheClient;

    /**
     * 查询积分中心数据。
     */
    @Override
    public AppPointsVo getDashboard() {
        ReaderPointsState state = loadState();
        return toVo(state);
    }

    /**
     * 领取每日签到积分。
     */
    @Override
    public AppPointsVo claimDailyCheckin() {
        visitorAccountService.requireLoggedInReaderId();
        ReaderPointsState state = loadState();
        LocalDate today = LocalDate.now();
        if (today.equals(state.getCheckinDate())) {
            return toVo(state);
        }
        if (state.getCheckinDate() == null || today.minusDays(1).equals(state.getCheckinDate())) {
            state.setDays(state.getDays() + 1);
        } else {
            state.setDays(1);
        }
        state.setCheckinDate(today);
        state.setClaimed(Boolean.TRUE);
        state.setTodayPoints(state.getTodayPoints() + 10);
        state.setTotalPoints(state.getTotalPoints() + 10);
        saveState(state);
        return toVo(state);
    }

    /**
     * 领取某个任务积分。
     */
    @Override
    public AppPointsVo claimTask(String taskKey) {
        visitorAccountService.requireLoggedInReaderId();
        ReaderPointsState state = loadState();
        PointsTask task = resolveTask(taskKey);
        if (task == null) {
            throw new ServiceException("任务不存在");
        }
        if (state.getCompletedTaskKeys().contains(taskKey)) {
            return toVo(state);
        }
        state.getCompletedTaskKeys().add(taskKey);
        state.setTodayPoints(state.getTodayPoints() + task.reward);
        state.setTotalPoints(state.getTotalPoints() + task.reward);
        saveState(state);
        return toVo(state);
    }

    /**
     * 读取当前读者积分状态。
     */
    private ReaderPointsState loadState() {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        String accountType = visitorAccountService.resolveCurrentAccountType();
        ReaderPointsState state = readerId == null ? null : redisCacheClient.getObjectSafely(cacheKey(readerId, accountType));
        if (state == null) {
            state = buildDefaultState(readerId, accountType);
        }
        normalizeState(state);
        if (readerId != null) {
            redisCacheClient.setObject(cacheKey(readerId, accountType), state);
        }
        return state;
    }

    /**
     * 保存当前读者积分状态。
     */
    private void saveState(ReaderPointsState state) {
        if (state.getReaderId() == null) {
            return;
        }
        redisCacheClient.setObject(cacheKey(state.getReaderId(), state.getAccountType()), state);
    }

    /**
     * 生成缓存键。
     */
    private String cacheKey(Long readerId, String accountType) {
        return POINTS_STATE_PREFIX + accountType + ":" + readerId;
    }

    /**
     * 构造默认积分状态。
     */
    private ReaderPointsState buildDefaultState(Long readerId, String accountType) {
        ReaderPointsState state = new ReaderPointsState();
        state.setReaderId(readerId);
        state.setAccountType(accountType);
        state.setTotalPoints(DEFAULT_TOTAL_POINTS);
        state.setTodayPoints(DEFAULT_TODAY_POINTS);
        state.setDays(DEFAULT_STREAK_DAYS);
        state.setClaimed(Boolean.FALSE);
        state.setPointDate(LocalDate.now());
        state.setCheckinDate(LocalDate.now().minusDays(1));
        state.setCompletedTaskKeys(new LinkedHashSet<>(Set.of("read")));
        return state;
    }

    /**
     * 把状态补足为前端可直接消费的结构。
     */
    private void normalizeState(ReaderPointsState state) {
        LocalDate today = LocalDate.now();
        if (state.getPointDate() == null || !today.equals(state.getPointDate())) {
            state.setTodayPoints(0);
            state.setPointDate(today);
        }
        state.setClaimed(today.equals(state.getCheckinDate()));
        if (state.getCompletedTaskKeys() == null) {
            state.setCompletedTaskKeys(new LinkedHashSet<>());
        }
        if (state.getCompletedTaskKeys().contains("read") && state.getTotalPoints() == null) {
            state.setTotalPoints(DEFAULT_TOTAL_POINTS);
        }
        if (state.getDays() == null || state.getDays() < 0) {
            state.setDays(DEFAULT_STREAK_DAYS);
        }
        if (state.getTotalPoints() == null) {
            state.setTotalPoints(DEFAULT_TOTAL_POINTS);
        }
        if (state.getTodayPoints() == null) {
            state.setTodayPoints(DEFAULT_TODAY_POINTS);
        }
    }

    /**
     * 转换为前端视图对象。
     */
    private AppPointsVo toVo(ReaderPointsState state) {
        AppPointsVo vo = new AppPointsVo();
        vo.setDays(state.getDays());
        vo.setClaimed(state.getClaimed());
        vo.setTodayPoints(state.getTodayPoints());
        vo.setTotalPoints(state.getTotalPoints());
        vo.setCompletedTaskKeys(new ArrayList<>(state.getCompletedTaskKeys()));
        vo.setTasks(defaultTasks().stream()
            .peek(task -> task.setCompleted(state.getCompletedTaskKeys().contains(task.getKey())))
            .toList());
        vo.setRewards(defaultRewards());
        return vo;
    }

    /**
     * 阅读任务配置。
     */
    private List<AppPointsTaskVo> defaultTasks() {
        List<AppPointsTaskVo> tasks = new ArrayList<>();
        tasks.add(task("read", "阅读20分钟", "连续阅读满20分钟即可领取", 10, "已完成", "/assets/task-read.svg"));
        tasks.add(task("review", "发表书评", "写下 20 字以上真实评价", 15, "去写评", "/assets/task-review.svg"));
        tasks.add(task("share", "分享好书", "分享给好友或收藏到书架", 8, "去分享", "/assets/task-share.svg"));
        tasks.add(task("listen", "听书30分钟", "在听书页连续播放30分钟", 12, "去听书", "/assets/task-listen.svg"));
        return tasks;
    }

    /**
     * 阅读成就配置。
     */
    private List<AppPointsRewardVo> defaultRewards() {
        List<AppPointsRewardVo> rewards = new ArrayList<>();
        rewards.add(reward("连续阅读徽章", 180, "点亮连续阅读成就"));
        rewards.add(reward("护眼背景", 260, "解锁一组护眼阅读背景"));
        rewards.add(reward("阅读称号", 420, "解锁专属阅读称号"));
        return rewards;
    }

    /**
     * 构造单个任务配置。
     */
    private AppPointsTaskVo task(String key, String title, String note, int reward, String action, String art) {
        AppPointsTaskVo vo = new AppPointsTaskVo();
        vo.setKey(key);
        vo.setTitle(title);
        vo.setNote(note);
        vo.setReward(reward);
        vo.setAction(action);
        vo.setArt(art);
        return vo;
    }

    /**
     * 构造单个奖励配置。
     */
    private AppPointsRewardVo reward(String title, int cost, String desc) {
        AppPointsRewardVo vo = new AppPointsRewardVo();
        vo.setTitle(title);
        vo.setCost(cost);
        vo.setDesc(desc);
        return vo;
    }

    /**
     * 任务配置。
     */
    private PointsTask resolveTask(String key) {
        if ("read".equals(key)) {
            return new PointsTask(10);
        }
        if ("review".equals(key)) {
            return new PointsTask(15);
        }
        if ("share".equals(key)) {
            return new PointsTask(8);
        }
        if ("listen".equals(key)) {
            return new PointsTask(12);
        }
        return null;
    }

    /**
     * 任务配置定义。
     */
    private record PointsTask(int reward) {
    }

    /**
     * 积分状态。
     */
    @Data
    public static class ReaderPointsState implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 读者主体ID。
         */
        private Long readerId;

        /**
         * 读者身份类型。
         */
        private String accountType;

        /**
         * 连续签到天数。
         */
        private Integer days;

        /**
         * 今日是否已签到。
         */
        private Boolean claimed;

        /**
         * 今日已得积分。
         */
        private Integer todayPoints;

        /**
         * 累计积分。
         */
        private Integer totalPoints;

        /**
         * 今日积分所属日期。
         */
        private LocalDate pointDate;

        /**
         * 最近签到日期。
         */
        private LocalDate checkinDate;

        /**
         * 已完成任务集合。
         */
        private Set<String> completedTaskKeys = new LinkedHashSet<>();
    }
}
