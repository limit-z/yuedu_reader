package org.dromara.reader.service.cache;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.reader.constant.ReaderConstants;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.springframework.stereotype.Service;

/**
 * 阅读器进度缓存服务，统一封装阅读进度的缓存读写与快照同步。
 */
@RequiredArgsConstructor
@Service
public class ReaderProgressCacheService {

    /**
     * 阅读进度脏标记前缀，用于区分“仅缓存”与“待刷库”两类数据。
     */
    private static final String DIRTY_PROGRESS_PREFIX = ReaderConstants.CACHE_READING_PROGRESS + "dirty:";

    /**
     * 阅读进度访问入口，负责缓存未命中时的数据库回源。
     */
    private final ReaderReadingProgressMapper readingProgressMapper;

    /**
     * Redis 客户端封装，负责统一执行阅读进度缓存读写。
     */
    private final ReaderRedisCacheClient redisCacheClient;

    /**
     * 生成阅读进度缓存键。
     */
    public String progressKey(Long userId, Long workId) {
        return ReaderConstants.CACHE_READING_PROGRESS + userId + ":" + workId;
    }

    /**
     * 生成脏进度标记键。
     */
    public String progressDirtyKey(Long userId, Long workId) {
        return DIRTY_PROGRESS_PREFIX + userId + ":" + workId;
    }

    /**
     * 读取阅读进度缓存。
     */
    public ReaderReadingProgress getProgress(Long userId, Long workId) {
        return redisCacheClient.getObject(progressKey(userId, workId));
    }

    /**
     * 写入可回刷数据库的进度缓存。
     */
    public void cacheProgress(ReaderReadingProgress progress) {
        cacheProgress(progress, true);
    }

    /**
     * 写入只读快照缓存。
     */
    public void cacheSnapshot(ReaderReadingProgress progress) {
        cacheProgress(progress, false);
    }

    /**
     * 写入进度缓存并按需打脏标记。
     */
    private void cacheProgress(ReaderReadingProgress progress, boolean dirty) {
        if (progress == null || progress.getUserId() == null || progress.getWorkId() == null) {
            return;
        }
        redisCacheClient.setObject(progressKey(progress.getUserId(), progress.getWorkId()), progress);
        if (dirty) {
            redisCacheClient.setObject(progressDirtyKey(progress.getUserId(), progress.getWorkId()), Boolean.TRUE);
        }
    }

    /**
     * 将脏进度缓存刷回数据库。
     */
    public boolean flushProgress(Long userId, Long workId) {
        if (userId == null || workId == null) {
            return false;
        }
        String dirtyKey = progressDirtyKey(userId, workId);
        // 没有脏标记时直接跳过，避免无意义地访问数据库。
        if (!Boolean.TRUE.equals(redisCacheClient.getObject(dirtyKey))) {
            return false;
        }
        ReaderReadingProgress cachedProgress = getProgress(userId, workId);
        if (cachedProgress == null) {
            redisCacheClient.deleteObject(dirtyKey);
            return false;
        }

        ReaderReadingProgress existingProgress = readingProgressMapper.selectOne(Wrappers.<ReaderReadingProgress>lambdaQuery()
            .eq(ReaderReadingProgress::getUserId, userId)
            .eq(ReaderReadingProgress::getWorkId, workId));
        if (existingProgress == null) {
            readingProgressMapper.insert(copyProgress(cachedProgress, null));
            redisCacheClient.deleteObject(dirtyKey);
            return true;
        }
        // 数据库里已有更新进度时，回滚缓存到数据库快照，防止旧缓存反向覆盖新数据。
        if (existingProgress.getProgressUpdatedAt() != null
            && cachedProgress.getProgressUpdatedAt() != null
            && cachedProgress.getProgressUpdatedAt().isBefore(existingProgress.getProgressUpdatedAt())) {
            cacheSnapshot(existingProgress);
            redisCacheClient.deleteObject(dirtyKey);
            return false;
        }
        ReaderReadingProgress mergedProgress = copyProgress(cachedProgress, existingProgress.getId());
        readingProgressMapper.updateById(mergedProgress);
        redisCacheClient.deleteObject(dirtyKey);
        return true;
    }

    /**
     * 复制阅读进度实体。
     */
    private ReaderReadingProgress copyProgress(ReaderReadingProgress source, Long id) {
        ReaderReadingProgress target = new ReaderReadingProgress();
        target.setId(id);
        target.setUserId(source.getUserId());
        target.setWorkId(source.getWorkId());
        target.setContentType(source.getContentType());
        target.setChapterId(source.getChapterId());
        target.setLocationValue(source.getLocationValue());
        target.setProgressPercent(source.getProgressPercent());
        target.setClientType(source.getClientType());
        target.setProgressUpdatedAt(source.getProgressUpdatedAt());
        return target;
    }
}
