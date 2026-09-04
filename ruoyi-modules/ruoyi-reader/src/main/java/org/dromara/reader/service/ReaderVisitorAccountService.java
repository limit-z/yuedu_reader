package org.dromara.reader.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.reader.constant.ReaderConstants;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderReadingHistory;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderVisitorAccount;
import org.dromara.reader.domain.vo.app.ReaderProfileVo;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderVisitorAccountMapper;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读器访客账户服务，负责把登录用户与游客态统一成同一套读者主体解析逻辑。
 */
@Service
@RequiredArgsConstructor
public class ReaderVisitorAccountService {

    /**
     * 访客账户访问入口，负责 visitorId 与游客主体ID 的映射持久化。
     */
    private final ReaderVisitorAccountMapper visitorAccountMapper;

    /**
     * 书架访问入口，负责游客态合并时迁移书架记录。
     */
    private final ReaderBookshelfMapper bookshelfMapper;

    /**
     * 阅读历史访问入口，负责游客态合并时迁移最近阅读记录。
     */
    private final ReaderReadingHistoryMapper readingHistoryMapper;

    /**
     * 阅读进度访问入口，负责游客态合并时迁移进度快照。
     */
    private final ReaderReadingProgressMapper readingProgressMapper;

    /**
     * 解析当前读者主体ID，优先使用登录用户，未登录时回退到访客账户。
     */
    public Long resolveCurrentReaderId() {
        Long loginUserId = LoginHelper.getUserId();
        if (loginUserId != null) {
            return loginUserId;
        }
        String visitorId = resolveCurrentVisitorId();
        if (StringUtils.isBlank(visitorId)) {
            return null;
        }
        ReaderVisitorAccount visitorAccount = getOrCreateVisitorAccount(visitorId);
        return visitorAccount == null ? null : visitorAccount.getId();
    }

    /**
     * 校验并返回当前可读写的读者主体ID。
     */
    public Long requireCurrentReaderId() {
        Long readerId = resolveCurrentReaderId();
        if (readerId == null) {
            throw new ServiceException("请先登录");
        }
        return readerId;
    }

    /**
     * 判断当前请求是否处于游客模式。
     */
    public boolean isVisitorMode() {
        return LoginHelper.getUserId() == null && StringUtils.isNotBlank(resolveCurrentVisitorId());
    }

    /**
     * 构造游客态个人资料，用于“我的”页在未登录时展示基础信息。
     */
    public ReaderProfileVo buildVisitorProfile() {
        ReaderVisitorAccount account = getCurrentVisitorAccount();
        ReaderProfileVo profile = new ReaderProfileVo();
        profile.setUserId(0L);
        profile.setNickName(account != null && StrUtil.isNotBlank(account.getNickName()) ? account.getNickName() : "游客");
        profile.setAvatarStyle(account == null ? null : account.getAvatarStyle());
        profile.setGender(account != null && StrUtil.isNotBlank(account.getGender()) ? account.getGender() : "UNKNOWN");
        profile.setMobile(account != null ? account.getMobile() : "未登录，当前使用访客云端数据");
        profile.setWechatNo(account == null ? null : account.getWechatNo());
        profile.setVisitorMode(Boolean.TRUE);
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("storageMode", "visitor-cloud");
        preferences.put("visitorId", resolveCurrentVisitorId());
        if (account != null && StrUtil.isNotBlank(account.getPreferencesJson())) {
            preferences.put("rawPreferencesJson", account.getPreferencesJson());
        }
        profile.setPreferences(preferences);
        return profile;
    }

    /**
     * 获取当前访客账户，用于游客态资料展示与更新。
     */
    public ReaderVisitorAccount getCurrentVisitorAccount() {
        String visitorId = resolveCurrentVisitorId();
        if (StrUtil.isBlank(visitorId)) {
            return null;
        }
        return getOrCreateVisitorAccount(visitorId);
    }

    /**
     * 解析当前读者身份类型。
     */
    public String resolveCurrentAccountType() {
        return LoginHelper.getUserId() != null ? "USER" : "VISITOR";
    }

    /**
     * 读取当前请求里的 visitorId。
     */
    public String resolveCurrentVisitorId() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return null;
        }
        String headerVisitorId = ServletUtils.getHeader(request, ReaderConstants.VISITOR_ID_HEADER);
        if (StrUtil.isNotBlank(headerVisitorId)) {
            return truncateVisitorId(headerVisitorId);
        }
        String paramVisitorId = request.getParameter(ReaderConstants.VISITOR_ID_PARAM);
        if (StrUtil.isNotBlank(paramVisitorId)) {
            return truncateVisitorId(paramVisitorId);
        }
        return null;
    }

    /**
     * 把访客态的书架、历史、进度迁移到指定系统用户下，供后续真实登录后做数据合并。
     */
    public void mergeVisitorData(String visitorId, Long targetUserId) {
        if (targetUserId == null || StrUtil.isBlank(visitorId)) {
            return;
        }
        ReaderVisitorAccount visitorAccount = visitorAccountMapper.selectOne(Wrappers.<ReaderVisitorAccount>lambdaQuery()
            .eq(ReaderVisitorAccount::getVisitorId, truncateVisitorId(visitorId))
            .last("limit 1"));
        if (visitorAccount == null || targetUserId.equals(visitorAccount.getId())) {
            return;
        }
        Long visitorReaderId = visitorAccount.getId();

        // 先迁移书架，保证目标账号进入后可直接看到游客期间收藏的作品。
        mergeBookshelf(visitorReaderId, targetUserId);
        // 再迁移阅读进度，让继续阅读入口能落到游客期间的最新位置。
        mergeProgress(visitorReaderId, targetUserId);
        // 最后迁移最近阅读历史，保留游客阶段形成的浏览轨迹。
        mergeHistory(visitorReaderId, targetUserId);

        visitorAccount.setLinkedUserId(targetUserId);
        visitorAccountMapper.updateById(visitorAccount);
    }

    /**
     * 获取或创建访客账户。
     */
    private ReaderVisitorAccount getOrCreateVisitorAccount(String visitorId) {
        ReaderVisitorAccount account = visitorAccountMapper.selectOne(Wrappers.<ReaderVisitorAccount>lambdaQuery()
            .eq(ReaderVisitorAccount::getVisitorId, visitorId)
            .last("limit 1"));
        if (account != null) {
            return account;
        }
        ReaderVisitorAccount created = new ReaderVisitorAccount();
        created.setVisitorId(visitorId);
        try {
            visitorAccountMapper.insert(created);
        } catch (Exception ignored) {
            // 并发首次访问时可能同时创建，失败后回查已存在记录即可。
        }
        return visitorAccountMapper.selectOne(Wrappers.<ReaderVisitorAccount>lambdaQuery()
            .eq(ReaderVisitorAccount::getVisitorId, visitorId)
            .last("limit 1"));
    }

    /**
     * 合并访客书架数据。
     */
    private void mergeBookshelf(Long visitorReaderId, Long targetUserId) {
        List<ReaderBookshelf> visitorBookshelves = bookshelfMapper.selectList(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, visitorReaderId));
        for (ReaderBookshelf visitorBookshelf : visitorBookshelves) {
            ReaderBookshelf targetBookshelf = bookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
                .eq(ReaderBookshelf::getUserId, targetUserId)
                .eq(ReaderBookshelf::getWorkId, visitorBookshelf.getWorkId())
                .last("limit 1"));
            if (targetBookshelf == null) {
                ReaderBookshelf created = new ReaderBookshelf();
                created.setUserId(targetUserId);
                created.setWorkId(visitorBookshelf.getWorkId());
                created.setTopPin(visitorBookshelf.getTopPin());
                created.setSortNo(visitorBookshelf.getSortNo());
                created.setUpdateTime(visitorBookshelf.getUpdateTime());
                bookshelfMapper.insert(created);
                continue;
            }
            if (visitorBookshelf.getUpdateTime() != null
                && (targetBookshelf.getUpdateTime() == null || visitorBookshelf.getUpdateTime().isAfter(targetBookshelf.getUpdateTime()))) {
                targetBookshelf.setTopPin(visitorBookshelf.getTopPin());
                targetBookshelf.setSortNo(visitorBookshelf.getSortNo());
                targetBookshelf.setUpdateTime(visitorBookshelf.getUpdateTime());
                bookshelfMapper.updateById(targetBookshelf);
            }
        }
        bookshelfMapper.delete(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, visitorReaderId));
    }

    /**
     * 合并访客阅读进度数据。
     */
    private void mergeProgress(Long visitorReaderId, Long targetUserId) {
        List<ReaderReadingProgress> visitorProgresses = readingProgressMapper.selectList(Wrappers.<ReaderReadingProgress>lambdaQuery()
            .eq(ReaderReadingProgress::getUserId, visitorReaderId));
        for (ReaderReadingProgress visitorProgress : visitorProgresses) {
            ReaderReadingProgress targetProgress = readingProgressMapper.selectOne(Wrappers.<ReaderReadingProgress>lambdaQuery()
                .eq(ReaderReadingProgress::getUserId, targetUserId)
                .eq(ReaderReadingProgress::getWorkId, visitorProgress.getWorkId())
                .last("limit 1"));
            if (targetProgress == null) {
                ReaderReadingProgress created = new ReaderReadingProgress();
                copyProgress(visitorProgress, created, targetUserId);
                readingProgressMapper.insert(created);
                continue;
            }
            LocalDateTime visitorUpdatedAt = visitorProgress.getProgressUpdatedAt();
            LocalDateTime targetUpdatedAt = targetProgress.getProgressUpdatedAt();
            if (visitorUpdatedAt != null && (targetUpdatedAt == null || visitorUpdatedAt.isAfter(targetUpdatedAt))) {
                copyProgress(visitorProgress, targetProgress, targetUserId);
                readingProgressMapper.updateById(targetProgress);
            }
        }
        readingProgressMapper.delete(Wrappers.<ReaderReadingProgress>lambdaQuery()
            .eq(ReaderReadingProgress::getUserId, visitorReaderId));
    }

    /**
     * 合并访客阅读历史数据。
     */
    private void mergeHistory(Long visitorReaderId, Long targetUserId) {
        List<ReaderReadingHistory> visitorHistories = readingHistoryMapper.selectList(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, visitorReaderId));
        for (ReaderReadingHistory visitorHistory : visitorHistories) {
            ReaderReadingHistory targetHistory = readingHistoryMapper.selectOne(Wrappers.<ReaderReadingHistory>lambdaQuery()
                .eq(ReaderReadingHistory::getUserId, targetUserId)
                .eq(ReaderReadingHistory::getWorkId, visitorHistory.getWorkId())
                .last("limit 1"));
            if (targetHistory == null) {
                ReaderReadingHistory created = new ReaderReadingHistory();
                copyHistory(visitorHistory, created, targetUserId);
                readingHistoryMapper.insert(created);
                continue;
            }
            LocalDateTime visitorReadAt = visitorHistory.getReadAt();
            LocalDateTime targetReadAt = targetHistory.getReadAt();
            if (visitorReadAt != null && (targetReadAt == null || visitorReadAt.isAfter(targetReadAt))) {
                copyHistory(visitorHistory, targetHistory, targetUserId);
                readingHistoryMapper.updateById(targetHistory);
            }
        }
        readingHistoryMapper.delete(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, visitorReaderId));
    }

    /**
     * 复制阅读进度核心字段。
     */
    private void copyProgress(ReaderReadingProgress source, ReaderReadingProgress target, Long targetUserId) {
        target.setUserId(targetUserId);
        target.setWorkId(source.getWorkId());
        target.setContentType(source.getContentType());
        target.setChapterId(source.getChapterId());
        target.setLocationValue(source.getLocationValue());
        target.setProgressPercent(source.getProgressPercent());
        target.setClientType(source.getClientType());
        target.setProgressUpdatedAt(source.getProgressUpdatedAt());
    }

    /**
     * 复制阅读历史核心字段。
     */
    private void copyHistory(ReaderReadingHistory source, ReaderReadingHistory target, Long targetUserId) {
        target.setUserId(targetUserId);
        target.setWorkId(source.getWorkId());
        target.setChapterId(source.getChapterId());
        target.setContentType(source.getContentType());
        target.setLocationValue(source.getLocationValue());
        target.setClientType(source.getClientType());
        target.setReadAt(source.getReadAt());
    }

    /**
     * 截断过长 visitorId，避免超出数据库字段长度。
     */
    private String truncateVisitorId(String visitorId) {
        return visitorId.length() > 64 ? visitorId.substring(0, 64) : visitorId;
    }
}
