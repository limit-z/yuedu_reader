package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.service.cache.ReaderProgressCacheService;
import org.springframework.stereotype.Component;

/**
 * 阅读器进度刷新任务，负责把缓存中的阅读进度批量刷回持久层。
 */
@RequiredArgsConstructor
@Component
public class ReaderProgressFlushJob {

    /**
     * 阅读进度缓存服务，负责把脏进度从缓存刷回数据库。
     */
    private final ReaderProgressCacheService progressCacheService;

    /**
     * 执行全量阅读进度刷新。
     */
    public void execute() {
        // flush buffered progress from redis to mysql
    }

    /**
     * 执行指定用户与作品的单条进度刷新。
     */
    public boolean execute(Long userId, Long workId) {
        return progressCacheService.flushProgress(userId, workId);
    }
}
