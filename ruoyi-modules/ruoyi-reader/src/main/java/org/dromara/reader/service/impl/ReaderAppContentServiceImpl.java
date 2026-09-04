package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderComicPage;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderAppContentService;
import org.dromara.reader.service.cache.ReaderWorkCacheService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 阅读器应用内容服务实现，负责向小程序与 H5 提供可阅读的作品详情、目录和正文。
 */
@RequiredArgsConstructor
@Service
public class ReaderAppContentServiceImpl implements IReaderAppContentService {

    /**
     * 作品不存在时统一复用的提示语，避免多处硬编码产生分叉。
     */
    private static final String WORK_NOT_FOUND_MESSAGE = "作品不存在";

    /**
     * 章节不存在时统一复用的提示语，保证详情页与阅读页提示口径一致。
     */
    private static final String CHAPTER_NOT_FOUND_MESSAGE = "章节不存在";

    /**
     * 作品主表访问入口，负责读取详情、作品类型与发布状态。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 小说章节访问入口，负责查询小说目录与正文。
     */
    private final ReaderNovelChapterMapper readerNovelChapterMapper;

    /**
     * 小说章节正文访问入口，负责按章节读取正文大字段。
     */
    private final ReaderNovelChapterContentMapper readerNovelChapterContentMapper;

    /**
     * 漫画章节访问入口，负责查询漫画目录与章节元数据。
     */
    private final ReaderComicChapterMapper readerComicChapterMapper;

    /**
     * 漫画页访问入口，负责读取章节对应的图片页列表。
     */
    private final ReaderComicPageMapper readerComicPageMapper;

    /**
     * 作品缓存服务，负责详情页与目录的高频缓存读写。
     */
    private final ReaderWorkCacheService readerWorkCacheService;

    /**
     * 书架访问入口，负责判断当前读者是否已加入书架。
     */
    private final ReaderBookshelfMapper readerBookshelfMapper;

    /**
     * 阅读进度访问入口，负责回填继续阅读位置。
     */
    private final ReaderReadingProgressMapper readerReadingProgressMapper;

    /**
     * 访客账户服务，负责解析登录态和游客态的当前读者主体。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 获取作品详情。
     */
    @Override
    public AppWorkDetailVo getWorkDetail(Long workId) {
        // 详情是高频读取数据，优先命中缓存可以减少首页、详情页反复进库的压力。
        AppWorkDetailVo cachedDetail = readerWorkCacheService.getWorkDetail(workId);
        if (cachedDetail != null) {
            return enrichWorkDetail(copyDetail(cachedDetail), workId);
        }
        ReaderWork work = requirePublishedWork(workId);
        AppWorkDetailVo baseDetail = buildBaseWorkDetail(work);
        readerWorkCacheService.cacheWorkDetail(baseDetail);
        return enrichWorkDetail(copyDetail(baseDetail), workId);
    }

    /**
     * 获取作品目录。
     */
    @Override
    public List<AppCatalogItemVo> getCatalog(Long workId) {
        List<AppCatalogItemVo> cachedCatalog = readerWorkCacheService.getCatalog(workId);
        if (cachedCatalog != null) {
            return cachedCatalog;
        }
        ReaderWork work = requirePublishedWork(workId);
        // App 侧目录只返回已发布章节，避免草稿内容从小程序或 H5 被提前暴露。
        if (WorkType.NOVEL.name().equals(work.getWorkType())) {
            List<AppCatalogItemVo> catalog = readerNovelChapterMapper.selectList(publishedNovelChapterQuery(workId)).stream()
                .map(chapter -> {
                    AppCatalogItemVo vo = new AppCatalogItemVo();
                    vo.setChapterId(chapter.getId());
                    vo.setChapterName(chapter.getChapterName());
                    vo.setChapterNo(chapter.getChapterNo());
                    vo.setVolumeName(chapter.getVolumeName());
                    return vo;
                })
                .toList();
            readerWorkCacheService.cacheCatalog(workId, catalog);
            return catalog;
        }
        if (WorkType.COMIC.name().equals(work.getWorkType())) {
            List<AppCatalogItemVo> catalog = readerComicChapterMapper.selectList(publishedComicChapterQuery(workId)).stream()
                .map(chapter -> {
                    AppCatalogItemVo vo = new AppCatalogItemVo();
                    vo.setChapterId(chapter.getId());
                    vo.setChapterName(chapter.getChapterName());
                    vo.setChapterNo(chapter.getChapterNo());
                    return vo;
                })
                .toList();
            readerWorkCacheService.cacheCatalog(workId, catalog);
            return catalog;
        }
        throw new ServiceException(WORK_NOT_FOUND_MESSAGE);
    }

    /**
     * 分页获取作品目录。
     */
    @Override
    public AppPageVo<AppCatalogItemVo> getCatalogPage(Long workId, Integer pageNum, Integer pageSize) {
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        AppPageVo<AppCatalogItemVo> cachedPage = readerWorkCacheService.getCatalogPage(workId, currentPage, currentSize);
        if (cachedPage != null) {
            return cachedPage;
        }
        ReaderWork work = requirePublishedWork(workId);

        AppPageVo<AppCatalogItemVo> pageVo = new AppPageVo<>();
        pageVo.setPageNum(currentPage);
        pageVo.setPageSize(currentSize);

        if (WorkType.NOVEL.name().equals(work.getWorkType())) {
            Page<ReaderNovelChapter> page = readerNovelChapterMapper.selectPage(
                new Page<>(currentPage, currentSize),
                publishedNovelChapterQuery(workId)
            );
            pageVo.setList(page.getRecords().stream().map(this::toNovelCatalogItem).toList());
            pageVo.setTotal(page.getTotal());
            pageVo.setTotalPages((int) page.getPages());
        } else if (WorkType.COMIC.name().equals(work.getWorkType())) {
            Page<ReaderComicChapter> page = readerComicChapterMapper.selectPage(
                new Page<>(currentPage, currentSize),
                publishedComicChapterQuery(workId)
            );
            pageVo.setList(page.getRecords().stream().map(this::toComicCatalogItem).toList());
            pageVo.setTotal(page.getTotal());
            pageVo.setTotalPages((int) page.getPages());
        } else {
            throw new ServiceException(WORK_NOT_FOUND_MESSAGE);
        }

        readerWorkCacheService.cacheCatalogPage(workId, currentPage, currentSize, pageVo);
        return pageVo;
    }

    /**
     * 获取相关推荐。
     */
    @Override
    public List<AppWorkCardVo> getRecommendations(Long workId) {
        ReaderWork currentWork = requirePublishedWork(workId);
        // 先取同作品类型候选，再把同分类内容排在前面，避免把小说/漫画类型误当作内容分类。
        List<ReaderWork> candidates = readerWorkMapper.selectList(Wrappers.<ReaderWork>lambdaQuery()
                .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name())
                .eq(ReaderWork::getWorkType, currentWork.getWorkType())
                .ne(ReaderWork::getId, workId)
                .orderByDesc(ReaderWork::getUpdateTime)
                .orderByDesc(ReaderWork::getCreateTime)
                .last("limit 20"));
        Map<Long, ReaderWork> unique = candidates.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(ReaderWork::getId, item -> item, (first, ignored) -> first, LinkedHashMap::new));
        return unique.values().stream()
            .sorted(Comparator.comparing(item -> !Objects.equals(currentWork.getCategoryName(), item.getCategoryName())))
            .limit(6)
            .map(this::toWorkCard)
            .toList();
    }

    /**
     * 获取小说章节正文。
     */
    @Override
    public AppNovelChapterVo getNovelChapter(Long chapterId) {
        return getNovelChapter(null, chapterId);
    }

    /**
     * 获取小说章节正文；调用方提供作品标识时可直接按组合键命中 Redis。
     */
    @Override
    public AppNovelChapterVo getNovelChapter(Long workId, Long chapterId) {
        if (workId != null) {
            AppNovelChapterVo cachedChapter = readerWorkCacheService.getNovelChapter(workId, chapterId);
            if (cachedChapter != null) {
                return cachedChapter;
            }
        }
        // 章节正文入口会同时校验章节与作品的发布状态，防止孤儿章节直接被访问。
        ReaderNovelChapter chapter = readerNovelChapterMapper.selectOne(Wrappers.<ReaderNovelChapter>lambdaQuery()
            .eq(ReaderNovelChapter::getId, chapterId)
            .eq(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (chapter == null || workId != null && !workId.equals(chapter.getWorkId())) {
            throw new ServiceException(CHAPTER_NOT_FOUND_MESSAGE);
        }
        ReaderWork work = requireReadableWork(chapter.getWorkId());
        if (!WorkType.NOVEL.name().equals(work.getWorkType())) {
            throw new ServiceException(CHAPTER_NOT_FOUND_MESSAGE);
        }
        if (workId == null) {
            AppNovelChapterVo cachedChapter = readerWorkCacheService.getNovelChapter(work.getId(), chapterId);
            if (cachedChapter != null) {
                return cachedChapter;
            }
        }

        AppNovelChapterVo vo = new AppNovelChapterVo();
        vo.setChapterId(chapter.getId());
        vo.setChapterName(chapter.getChapterName());
        vo.setContent(resolveNovelChapterContent(chapter));
        vo.setWordCount(chapter.getWordCount());
        readerWorkCacheService.cacheNovelChapter(work.getId(), vo);
        return vo;
    }

    /**
     * 获取漫画章节图片列表。
     */
    @Override
    public AppComicChapterVo getComicChapter(Long chapterId) {
        return getComicChapter(null, chapterId);
    }

    /**
     * 获取漫画章节内容；调用方提供作品标识时可直接按组合键命中 Redis。
     */
    @Override
    public AppComicChapterVo getComicChapter(Long workId, Long chapterId) {
        if (workId != null) {
            AppComicChapterVo cachedChapter = readerWorkCacheService.getComicChapter(workId, chapterId);
            if (cachedChapter != null) {
                return cachedChapter;
            }
        }
        // 漫画正文除了校验发布态，还会按页序返回图片列表给前端阅读器直接渲染。
        ReaderComicChapter chapter = readerComicChapterMapper.selectOne(Wrappers.<ReaderComicChapter>lambdaQuery()
            .eq(ReaderComicChapter::getId, chapterId)
            .eq(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (chapter == null || workId != null && !workId.equals(chapter.getWorkId())) {
            throw new ServiceException(CHAPTER_NOT_FOUND_MESSAGE);
        }
        ReaderWork work = requireReadableWork(chapter.getWorkId());
        if (!WorkType.COMIC.name().equals(work.getWorkType())) {
            throw new ServiceException(CHAPTER_NOT_FOUND_MESSAGE);
        }
        if (workId == null) {
            AppComicChapterVo cachedChapter = readerWorkCacheService.getComicChapter(work.getId(), chapterId);
            if (cachedChapter != null) {
                return cachedChapter;
            }
        }

        AppComicChapterVo vo = new AppComicChapterVo();
        vo.setChapterId(chapter.getId());
        vo.setChapterName(chapter.getChapterName());
        vo.setPageCount(chapter.getPageCount());
        vo.setImageUrls(readerComicPageMapper.selectList(Wrappers.<ReaderComicPage>lambdaQuery()
                .eq(ReaderComicPage::getChapterId, chapterId)
                .orderByAsc(ReaderComicPage::getPageNo))
            .stream()
            .map(ReaderComicPage::getImageUrl)
            .toList());
        readerWorkCacheService.cacheComicChapter(work.getId(), vo);
        return vo;
    }

    /**
     * 校验作品是否处于已发布状态。
     */
    private ReaderWork requirePublishedWork(Long workId) {
        ReaderWork work = readerWorkMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getId, workId)
            .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (work == null) {
            throw new ServiceException(WORK_NOT_FOUND_MESSAGE);
        }
        return work;
    }

    /**
     * 校验作品是否仍可被章节阅读入口访问。
     */
    private ReaderWork requireReadableWork(Long workId) {
        ReaderWork work = readerWorkMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getId, workId)
            .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (work == null) {
            throw new ServiceException(CHAPTER_NOT_FOUND_MESSAGE);
        }
        return work;
    }

    /**
     * 构造已发布小说章节查询条件。
     */
    private LambdaQueryWrapper<ReaderNovelChapter> publishedNovelChapterQuery(Long workId) {
        return Wrappers.<ReaderNovelChapter>lambdaQuery()
            .eq(ReaderNovelChapter::getWorkId, workId)
            .eq(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
            .orderByAsc(ReaderNovelChapter::getChapterNo);
    }

    /**
     * 将小说章节元数据转换成读者端目录项。
     */
    private AppCatalogItemVo toNovelCatalogItem(ReaderNovelChapter chapter) {
        AppCatalogItemVo vo = new AppCatalogItemVo();
        vo.setChapterId(chapter.getId());
        vo.setChapterName(chapter.getChapterName());
        vo.setChapterNo(chapter.getChapterNo());
        vo.setVolumeName(chapter.getVolumeName());
        return vo;
    }

    /**
     * 将漫画章节元数据转换成读者端目录项。
     */
    private AppCatalogItemVo toComicCatalogItem(ReaderComicChapter chapter) {
        AppCatalogItemVo vo = new AppCatalogItemVo();
        vo.setChapterId(chapter.getId());
        vo.setChapterName(chapter.getChapterName());
        vo.setChapterNo(chapter.getChapterNo());
        return vo;
    }

    /**
     * 读取小说章节正文，并兼容分表改造前仍保存在旧表中的历史数据。
     */
    private String resolveNovelChapterContent(ReaderNovelChapter chapter) {
        ReaderNovelChapterContent content = readerNovelChapterContentMapper.selectById(chapter.getId());
        if (content != null) {
            return content.getContent();
        }
        return readerNovelChapterMapper.selectLegacyContentByChapterId(chapter.getId());
    }

    /**
     * 构造已发布漫画章节查询条件。
     */
    private LambdaQueryWrapper<ReaderComicChapter> publishedComicChapterQuery(Long workId) {
        return Wrappers.<ReaderComicChapter>lambdaQuery()
            .eq(ReaderComicChapter::getWorkId, workId)
            .eq(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
            .orderByAsc(ReaderComicChapter::getChapterNo);
    }

    /**
     * 把作品实体转换成轻量推荐卡片，供详情页相关推荐直接复用。
     */
    private AppWorkCardVo toWorkCard(ReaderWork work) {
        AppWorkCardVo vo = new AppWorkCardVo();
        vo.setWorkId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setIntro(work.getIntro());
        vo.setWorkType(work.getWorkType());
        vo.setCategoryName(work.getCategoryName());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setTotalChapters(work.getTotalChapters());
        vo.setTotalPages(work.getTotalPages());
        return vo;
    }

    /**
     * 构建作品详情基础信息，基础部分可以安全缓存。
     */
    private AppWorkDetailVo buildBaseWorkDetail(ReaderWork work) {
        AppWorkDetailVo vo = new AppWorkDetailVo();
        vo.setWorkId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setIntro(work.getIntro());
        vo.setWorkType(work.getWorkType());
        vo.setCategoryName(work.getCategoryName());
        vo.setSerialStatus(work.getSerialStatus());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setTotalChapters(work.getTotalChapters());
        vo.setTotalPages(work.getTotalPages());
        vo.setUpdatedAt(work.getUpdateTime());

        AppCatalogItemVo latest = findLatestCatalogItem(work);
        if (latest != null) {
            vo.setLatestChapterId(latest.getChapterId());
            vo.setLatestChapterName(latest.getChapterName());
        }
        return vo;
    }

    /**
     * 只查询最新章节元数据，避免详情缓存冷启动时加载整本目录。
     */
    private AppCatalogItemVo findLatestCatalogItem(ReaderWork work) {
        if (WorkType.NOVEL.name().equals(work.getWorkType())) {
            ReaderNovelChapter latest = readerNovelChapterMapper.selectOne(
                Wrappers.<ReaderNovelChapter>lambdaQuery()
                    .eq(ReaderNovelChapter::getWorkId, work.getId())
                    .eq(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
                    .orderByDesc(ReaderNovelChapter::getChapterNo)
                    .last("limit 1")
            );
            return latest == null ? null : toNovelCatalogItem(latest);
        }
        if (WorkType.COMIC.name().equals(work.getWorkType())) {
            ReaderComicChapter latest = readerComicChapterMapper.selectOne(
                Wrappers.<ReaderComicChapter>lambdaQuery()
                    .eq(ReaderComicChapter::getWorkId, work.getId())
                    .eq(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
                    .orderByDesc(ReaderComicChapter::getChapterNo)
                    .last("limit 1")
            );
            return latest == null ? null : toComicCatalogItem(latest);
        }
        return null;
    }

    /**
     * 补充当前读者态的书架和继续阅读信息。
     */
    private AppWorkDetailVo enrichWorkDetail(AppWorkDetailVo detail, Long workId) {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId != null) {
            detail.setIsOnBookshelf(readerBookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
                .eq(ReaderBookshelf::getUserId, readerId)
                .eq(ReaderBookshelf::getWorkId, workId)
                .last("limit 1")) != null);

            ReaderReadingProgress progress = readerReadingProgressMapper.selectOne(Wrappers.<ReaderReadingProgress>lambdaQuery()
                .eq(ReaderReadingProgress::getUserId, readerId)
                .eq(ReaderReadingProgress::getWorkId, workId)
                .orderByDesc(ReaderReadingProgress::getProgressUpdatedAt)
                .orderByDesc(ReaderReadingProgress::getUpdateTime)
                .last("limit 1"));
            if (progress != null) {
                detail.setContinueChapterId(progress.getChapterId());
                detail.setContinueChapterName(resolveChapterName(detail.getWorkType(), progress.getChapterId()));
                detail.setContinueProgress(progress.getProgressPercent());
            }
        } else {
            detail.setIsOnBookshelf(Boolean.FALSE);
        }
        return detail;
    }

    /**
     * 复制详情对象，避免缓存对象被填充用户态字段。
     */
    private AppWorkDetailVo copyDetail(AppWorkDetailVo source) {
        AppWorkDetailVo vo = new AppWorkDetailVo();
        vo.setWorkId(source.getWorkId());
        vo.setTitle(source.getTitle());
        vo.setCoverUrl(source.getCoverUrl());
        vo.setIntro(source.getIntro());
        vo.setWorkType(source.getWorkType());
        vo.setCategoryName(source.getCategoryName());
        vo.setSerialStatus(source.getSerialStatus());
        vo.setPublishStatus(source.getPublishStatus());
        vo.setTotalChapters(source.getTotalChapters());
        vo.setTotalPages(source.getTotalPages());
        vo.setUpdatedAt(source.getUpdatedAt());
        vo.setLatestChapterId(source.getLatestChapterId());
        vo.setLatestChapterName(source.getLatestChapterName());
        vo.setIsOnBookshelf(source.getIsOnBookshelf());
        vo.setContinueChapterId(source.getContinueChapterId());
        vo.setContinueChapterName(source.getContinueChapterName());
        vo.setContinueProgress(source.getContinueProgress());
        return vo;
    }

    /**
     * 按作品类型解析章节名称。
     */
    private String resolveChapterName(String workType, Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        if (WorkType.NOVEL.name().equals(workType)) {
            ReaderNovelChapter chapter = readerNovelChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        if (WorkType.COMIC.name().equals(workType)) {
            ReaderComicChapter chapter = readerComicChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        return null;
    }
}
