package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;

import java.util.List;

/**
 * 阅读器模块代码，承载 IReaderAppContentService 相关业务能力。
 */
public interface IReaderAppContentService {

    /**
     * 获取作品详情。
     */
    AppWorkDetailVo getWorkDetail(Long workId);

    /**
     * 获取作品目录。
     */
    List<AppCatalogItemVo> getCatalog(Long workId);

    /**
     * 分页获取作品目录，供读者端按需预加载。
     */
    AppPageVo<AppCatalogItemVo> getCatalogPage(Long workId, Integer pageNum, Integer pageSize);

    /**
     * 获取作品相关推荐。
     */
    List<AppWorkCardVo> getRecommendations(Long workId);

    /**
     * 获取小说章节正文。
     */
    AppNovelChapterVo getNovelChapter(Long chapterId);

    /**
     * 按作品和章节获取小说正文，作品标识用于优先命中 Redis 缓存。
     */
    AppNovelChapterVo getNovelChapter(Long workId, Long chapterId);

    /**
     * 获取漫画章节图片列表。
     */
    AppComicChapterVo getComicChapter(Long chapterId);

    /**
     * 按作品和章节获取漫画内容，作品标识用于优先命中 Redis 缓存。
     */
    AppComicChapterVo getComicChapter(Long workId, Long chapterId);
}
