package org.dromara.reader.service.cache;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.constant.ReaderConstants;
import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 阅读器作品缓存服务，统一维护详情、目录等高频读取数据的缓存。
 */
@RequiredArgsConstructor
@Service
public class ReaderWorkCacheService {

    private static final Duration CONTENT_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * Redis 客户端封装，负责详情与目录缓存的统一读写。
     */
    private final ReaderRedisCacheClient redisCacheClient;

    /**
     * 读取作品详情缓存。
     */
    public AppWorkDetailVo getWorkDetail(Long workId) {
        return redisCacheClient.getObjectSafely(workDetailKey(workId));
    }

    /**
     * 写入作品详情缓存。
     */
    public void cacheWorkDetail(AppWorkDetailVo detail) {
        if (detail == null || detail.getWorkId() == null) {
            return;
        }
        redisCacheClient.setObject(workDetailKey(detail.getWorkId()), detail);
    }

    @SuppressWarnings("unchecked")
    /**
     * 读取作品目录缓存。
     */
    public List<AppCatalogItemVo> getCatalog(Long workId) {
        return redisCacheClient.getObjectSafely(workCatalogKey(workId));
    }

    /**
     * 写入作品目录缓存。
     */
    public void cacheCatalog(Long workId, List<AppCatalogItemVo> catalog) {
        if (workId == null || catalog == null) {
            return;
        }
        redisCacheClient.setObject(workCatalogKey(workId), catalog);
    }

    /**
     * 读取分页目录缓存。
     */
    public AppPageVo<AppCatalogItemVo> getCatalogPage(Long workId, Integer pageNum, Integer pageSize) {
        return redisCacheClient.getObjectSafely(catalogPageKey(workId, pageNum, pageSize));
    }

    /**
     * 写入分页目录缓存。
     */
    public void cacheCatalogPage(Long workId, Integer pageNum, Integer pageSize, AppPageVo<AppCatalogItemVo> catalogPage) {
        if (workId == null || pageNum == null || pageSize == null || catalogPage == null) {
            return;
        }
        redisCacheClient.setObject(catalogPageKey(workId, pageNum, pageSize), catalogPage, CONTENT_CACHE_TTL);
    }

    /**
     * 读取小说章节正文缓存。
     */
    public AppNovelChapterVo getNovelChapter(Long workId, Long chapterId) {
        return redisCacheClient.getObjectSafely(novelChapterKey(workId, chapterId));
    }

    /**
     * 写入小说章节正文缓存。
     */
    public void cacheNovelChapter(Long workId, AppNovelChapterVo chapter) {
        if (workId == null || chapter == null || chapter.getChapterId() == null) {
            return;
        }
        redisCacheClient.setObject(novelChapterKey(workId, chapter.getChapterId()), chapter, CONTENT_CACHE_TTL);
    }

    /**
     * 读取漫画章节缓存。
     */
    public AppComicChapterVo getComicChapter(Long workId, Long chapterId) {
        return redisCacheClient.getObjectSafely(comicChapterKey(workId, chapterId));
    }

    /**
     * 写入漫画章节缓存。
     */
    public void cacheComicChapter(Long workId, AppComicChapterVo chapter) {
        if (workId == null || chapter == null || chapter.getChapterId() == null) {
            return;
        }
        redisCacheClient.setObject(comicChapterKey(workId, chapter.getChapterId()), chapter, CONTENT_CACHE_TTL);
    }

    /**
     * 生成作品详情缓存键。
     */
    public String workDetailKey(Long workId) {
        return ReaderConstants.CACHE_WORK_DETAIL + workId;
    }

    /**
     * 生成作品目录缓存键。
     */
    public String workCatalogKey(Long workId) {
        return ReaderConstants.CACHE_WORK_CATALOG + workId;
    }

    /**
     * 生成分页目录缓存键。
     */
    public String catalogPageKey(Long workId, Integer pageNum, Integer pageSize) {
        return ReaderConstants.CACHE_WORK_CATALOG_PAGE + workId + ":" + pageNum + ":" + pageSize;
    }

    /**
     * 生成小说章节正文缓存键。
     */
    public String novelChapterKey(Long workId, Long chapterId) {
        return ReaderConstants.CACHE_NOVEL_CHAPTER + workId + ":" + chapterId;
    }

    public String comicChapterKey(Long workId, Long chapterId) {
        return ReaderConstants.CACHE_COMIC_CHAPTER + workId + ":" + chapterId;
    }

    /**
     * 清理作品详情与目录缓存。
     */
    public void evictPublishedWorkCaches(Long workId) {
        redisCacheClient.deleteObjects(List.of(workDetailKey(workId), workCatalogKey(workId)));
        redisCacheClient.deleteByPattern(ReaderConstants.CACHE_WORK_CATALOG_PAGE + workId + ":*");
        redisCacheClient.deleteByPattern(ReaderConstants.CACHE_NOVEL_CHAPTER + workId + ":*");
        redisCacheClient.deleteByPattern(ReaderConstants.CACHE_COMIC_CHAPTER + workId + ":*");
    }
}
