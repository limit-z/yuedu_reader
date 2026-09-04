package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.service.cache.ReaderWorkCacheService;
import org.springframework.stereotype.Component;

/**
 * 阅读器发布刷新任务，负责作品上下架后的缓存刷新。
 */
@RequiredArgsConstructor
@Component
public class ReaderPublishRefreshJob {

    /**
     * 作品缓存服务，负责发布状态变化后的详情与目录缓存失效。
     */
    private final ReaderWorkCacheService readerWorkCacheService;

    /**
     * 执行指定作品的发布缓存刷新。
     */
    public void execute(Long workId) {
        readerWorkCacheService.evictPublishedWorkCaches(workId);
    }
}
