package org.dromara.reader.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.reader.domain.ReaderReadingHistory;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;
import org.dromara.reader.domain.vo.app.AppReadingStatsCategoryVo;
import org.dromara.reader.domain.vo.app.AppReadingStatsDayVo;
import org.dromara.reader.domain.vo.app.AppReadingStatsVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderBookshelfService;
import org.dromara.reader.service.IReaderReadingStatsService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 阅读统计服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReaderReadingStatsServiceImpl implements IReaderReadingStatsService {

    /**
     * 阅读历史访问入口。
     */
    private final ReaderReadingHistoryMapper historyMapper;

    /**
     * 阅读进度访问入口。
     */
    private final ReaderReadingProgressMapper progressMapper;

    /**
     * 作品访问入口。
     */
    private final ReaderWorkMapper workMapper;

    /**
     * 书架服务，用于复用历史视图转换。
     */
    private final IReaderBookshelfService bookshelfService;

    /**
     * 读者主体解析服务。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 查询阅读统计。
     */
    @Override
    public AppReadingStatsVo getStats() {
        Long userId = visitorAccountService.requireCurrentReaderId();
        List<ReaderReadingHistory> histories = historyMapper.selectList(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, userId)
            .orderByDesc(ReaderReadingHistory::getReadAt, ReaderReadingHistory::getUpdateTime));
        List<ReaderReadingProgress> progresses = progressMapper.selectList(Wrappers.<ReaderReadingProgress>lambdaQuery()
            .eq(ReaderReadingProgress::getUserId, userId));

        AppReadingStatsVo vo = new AppReadingStatsVo();
        vo.setMonthTargetBooks(15);
        vo.setMonthReadBooks(countMonthReadBooks(histories));
        vo.setTotalReadBooks((int) histories.stream().map(ReaderReadingHistory::getWorkId).distinct().count());
        vo.setFinishedBooks((int) progresses.stream().filter(item -> item.getProgressPercent() != null && item.getProgressPercent() >= 100).count());
        vo.setEstimatedReadHours(estimateReadHours(histories, progresses));
        vo.setAverageDailyHours(round2(vo.getEstimatedReadHours() / Math.max(1, Math.min(30, YearMonth.now().lengthOfMonth()))));
        vo.setProgressPercent(Math.min(100, Math.round((vo.getMonthReadBooks() * 100f) / vo.getMonthTargetBooks())));
        vo.setWeeklyHours(buildWeeklyHours(histories, progresses));
        vo.setCategoryDistribution(buildCategoryDistribution(histories));
        vo.setRecentReads(buildRecentReads(histories));
        return vo;
    }

    /**
     * 统计本月读过的作品数。
     */
    private Integer countMonthReadBooks(List<ReaderReadingHistory> histories) {
        YearMonth currentMonth = YearMonth.now();
        return (int) histories.stream()
            .filter(history -> history.getReadAt() != null)
            .filter(history -> YearMonth.from(history.getReadAt()).equals(currentMonth))
            .map(ReaderReadingHistory::getWorkId)
            .distinct()
            .count();
    }

    /**
     * 估算总阅读小时数。
     */
    private Double estimateReadHours(List<ReaderReadingHistory> histories, List<ReaderReadingProgress> progresses) {
        double historyHours = histories.size() * 0.35d;
        double progressHours = progresses.stream()
            .mapToDouble(item -> item.getProgressPercent() == null ? 0d : item.getProgressPercent() / 100d * 1.8d)
            .sum();
        return round2(historyHours + progressHours);
    }

    /**
     * 构造本周阅读曲线。
     */
    private List<AppReadingStatsDayVo> buildWeeklyHours(List<ReaderReadingHistory> histories, List<ReaderReadingProgress> progresses) {
        Map<DayOfWeek, Double> dayHours = new LinkedHashMap<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            dayHours.put(day, 0d);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate weekStart = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);
        histories.stream()
            .filter(history -> history.getReadAt() != null)
            .filter(history -> !history.getReadAt().toLocalDate().isBefore(weekStart))
            .forEach(history -> {
                DayOfWeek day = history.getReadAt().getDayOfWeek();
                dayHours.put(day, round2(dayHours.get(day) + 0.35d));
            });
        progresses.stream()
            .filter(progress -> progress.getProgressUpdatedAt() != null)
            .filter(progress -> !progress.getProgressUpdatedAt().toLocalDate().isBefore(weekStart))
            .forEach(progress -> {
                DayOfWeek day = progress.getProgressUpdatedAt().getDayOfWeek();
                dayHours.put(day, round2(dayHours.get(day) + (progress.getProgressPercent() == null ? 0d : progress.getProgressPercent() / 100d * 0.3d)));
            });

        List<AppReadingStatsDayVo> result = new ArrayList<>();
        for (Map.Entry<DayOfWeek, Double> entry : dayHours.entrySet()) {
            AppReadingStatsDayVo vo = new AppReadingStatsDayVo();
            vo.setLabel("周" + switch (entry.getKey()) {
                case MONDAY -> "一";
                case TUESDAY -> "二";
                case WEDNESDAY -> "三";
                case THURSDAY -> "四";
                case FRIDAY -> "五";
                case SATURDAY -> "六";
                case SUNDAY -> "日";
            });
            vo.setHours(round2(entry.getValue()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 构造类别分布。
     */
    private List<AppReadingStatsCategoryVo> buildCategoryDistribution(List<ReaderReadingHistory> histories) {
        Map<String, Long> counts = histories.stream()
            .map(ReaderReadingHistory::getWorkId)
            .distinct()
            .map(this::loadWorkType)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(type -> type, LinkedHashMap::new, Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return List.of();
        }
        return counts.entrySet().stream()
            .map(entry -> {
                AppReadingStatsCategoryVo vo = new AppReadingStatsCategoryVo();
                vo.setName("NOVEL".equals(entry.getKey()) ? "小说" : "漫画");
                vo.setPercent((int) Math.round(entry.getValue() * 100d / total));
                return vo;
            })
            .toList();
    }

    /**
     * 加载作品类型。
     */
    private String loadWorkType(Long workId) {
        ReaderWork work = workMapper.selectById(workId);
        if (work == null || PublishStatus.PUBLISHED.name().equals(work.getPublishStatus()) == false) {
            return null;
        }
        return work.getWorkType();
    }

    /**
     * 构造最近阅读。
     */
    private List<AppHistoryItemVo> buildRecentReads(List<ReaderReadingHistory> histories) {
        if (CollUtil.isEmpty(histories)) {
            return List.of();
        }
        return bookshelfService.listHistory().stream().limit(3).toList();
    }

    /**
     * 保留两位小数。
     */
    private Double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
