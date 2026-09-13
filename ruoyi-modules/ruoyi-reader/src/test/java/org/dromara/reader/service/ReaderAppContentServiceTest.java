package org.dromara.reader.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderComicPage;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.cache.ReaderWorkCacheService;
import org.dromara.reader.service.impl.ReaderCoverService;
import org.dromara.reader.service.impl.ReaderAppContentServiceImpl;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderAppContentServiceTest {

    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderNovelChapterMapper readerNovelChapterMapper;
    @Mock
    private ReaderNovelChapterContentMapper readerNovelChapterContentMapper;
    @Mock
    private ReaderComicChapterMapper readerComicChapterMapper;
    @Mock
    private ReaderComicPageMapper readerComicPageMapper;
    @Mock
    private ReaderWorkCacheService readerWorkCacheService;
    @Mock
    private ReaderBookshelfMapper readerBookshelfMapper;
    @Mock
    private ReaderReadingProgressMapper readerReadingProgressMapper;
    @Mock
    private ReaderVisitorAccountService visitorAccountService;
    @Mock
    private ReaderCoverService readerCoverService;

    @InjectMocks
    private ReaderAppContentServiceImpl service;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderWork.class);
        initializeTableInfo(ReaderNovelChapter.class);
        initializeTableInfo(ReaderComicChapter.class);
        initializeTableInfo(ReaderComicPage.class);
    }

    @Test
    void getWorkDetailShouldQueryPublishedWorkOnly() {
        ReaderWork work = publishedWork(1L, WorkType.NOVEL);
        work.setTitle("边城");
        work.setIntro("湘西故事");
        work.setCoverUrl("https://img/cover.jpg");
        work.setTotalChapters(18);
        ReaderNovelChapter latestChapter = novelChapter(201L, 18, "第十八章", "尾声");
        when(readerNovelChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latestChapter);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderWork> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(1L, 1, PublishStatus.PUBLISHED.name(), 1));
            return work;
        });

        AppWorkDetailVo result = service.getWorkDetail(1L);

        assertEquals(1L, result.getWorkId());
        assertEquals("边城", result.getTitle());
        assertEquals("湘西故事", result.getIntro());
        assertEquals("https://img/cover.jpg", result.getCoverUrl());
        assertEquals(18, result.getTotalChapters());
        assertEquals(PublishStatus.PUBLISHED.name(), result.getPublishStatus());
        assertEquals(201L, result.getLatestChapterId());
        assertEquals("第十八章", result.getLatestChapterName());
        assertEquals(Boolean.FALSE, result.getIsOnBookshelf());
    }

    @Test
    void getWorkDetailShouldReturnCachedDetailBeforeQueryingMysql() {
        AppWorkDetailVo cached = new AppWorkDetailVo();
        cached.setWorkId(1L);
        cached.setTitle("缓存作品");
        when(readerWorkCacheService.getWorkDetail(1L)).thenReturn(cached);

        AppWorkDetailVo result = service.getWorkDetail(1L);

        assertEquals(1L, result.getWorkId());
        assertEquals("缓存作品", result.getTitle());
        verifyNoInteractions(readerWorkMapper);
    }

    @Test
    void getWorkDetailShouldCacheMysqlResultOnCacheMiss() {
        ReaderWork work = publishedWork(1L, WorkType.NOVEL);
        work.setTitle("边城");
        work.setIntro("湘西故事");
        work.setCoverUrl("https://img/cover.jpg");
        work.setTotalChapters(18);
        when(readerWorkCacheService.getWorkDetail(1L)).thenReturn(null);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(work);

        AppWorkDetailVo result = service.getWorkDetail(1L);

        assertEquals(1L, result.getWorkId());
        assertEquals("边城", result.getTitle());
        verify(readerWorkCacheService).cacheWorkDetail(any(AppWorkDetailVo.class));
    }

    @Test
    void getWorkDetailShouldExposeShelfAndContinueReadingState() {
        ReaderWork work = publishedWork(1L, WorkType.NOVEL);
        work.setTitle("边城");
        work.setIntro("湘西故事");
        work.setCoverUrl("https://img/cover.jpg");
        work.setTotalChapters(18);
        ReaderNovelChapter latestChapter = novelChapter(201L, 18, "第十八章", "尾声");

        ReaderBookshelf bookshelf = new ReaderBookshelf();
        bookshelf.setId(9L);
        bookshelf.setUserId(7L);
        bookshelf.setWorkId(1L);
        bookshelf.setTopPin("1");

        ReaderReadingProgress progress = new ReaderReadingProgress();
        progress.setWorkId(1L);
        progress.setChapterId(201L);
        progress.setProgressPercent(68);

        ReaderNovelChapter novelChapter = new ReaderNovelChapter();
        novelChapter.setId(201L);
        novelChapter.setChapterName("第二章");

        when(readerWorkCacheService.getWorkDetail(1L)).thenReturn(null);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(work);
        when(readerNovelChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latestChapter);
        when(visitorAccountService.resolveCurrentReaderId()).thenReturn(7L);
        when(readerBookshelfMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bookshelf);
        when(readerReadingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(progress);
        when(readerNovelChapterMapper.selectById(201L)).thenReturn(novelChapter);

        AppWorkDetailVo result = service.getWorkDetail(1L);

        assertEquals(Boolean.TRUE, result.getIsOnBookshelf());
        assertEquals(201L, result.getContinueChapterId());
        assertEquals("第二章", result.getContinueChapterName());
        assertEquals(68, result.getContinueProgress());
        assertEquals(201L, result.getLatestChapterId());
        assertEquals("第十八章", result.getLatestChapterName());
    }

    @Test
    void getCatalogShouldQueryPublishedNovelChaptersOrderedByChapterNo() {
        when(readerWorkCacheService.getCatalog(9L)).thenReturn(null);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(9L, WorkType.NOVEL));
        when(readerNovelChapterMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderNovelChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(9L, 1, PublishStatus.PUBLISHED.name(), 1));
            assertOrderBy(wrapper, "chapter_no ASC");
            return List.of(
                novelChapter(101L, 1, "第一章", "第一卷"),
                novelChapter(102L, 2, "第二章", "第二卷")
            );
        });

        List<AppCatalogItemVo> result = service.getCatalog(9L);

        assertEquals(2, result.size());
        assertEquals(101L, result.get(0).getChapterId());
        assertEquals("第一章", result.get(0).getChapterName());
        assertEquals("第一卷", result.get(0).getVolumeName());
        assertEquals(102L, result.get(1).getChapterId());
        verify(readerWorkCacheService).cacheCatalog(9L, result);
    }

    @Test
    void getCatalogPageShouldQueryAndCachePublishedNovelPage() {
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(9L, WorkType.NOVEL));
        when(readerWorkCacheService.getCatalogPage(9L, 1, 20)).thenReturn(null);
        when(readerNovelChapterMapper.selectPage(any(), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<ReaderNovelChapter> page = invocation.getArgument(0);
            page.setTotal(2);
            page.setRecords(List.of(
                novelChapter(101L, 1, "第一章", "第一卷"),
                novelChapter(102L, 2, "第二章", "第二卷")
            ));
            return page;
        });

        AppPageVo<AppCatalogItemVo> result = service.getCatalogPage(9L, 1, 20);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        verify(readerWorkCacheService).cacheCatalogPage(9L, 1, 20, result);
    }

    @Test
    void getCatalogPageShouldReturnRedisPageBeforeQueryingMysql() {
        AppPageVo<AppCatalogItemVo> cached = new AppPageVo<>();
        cached.setPageNum(1);
        cached.setPageSize(20);
        cached.setTotal(1L);
        cached.setList(List.of(new AppCatalogItemVo()));
        when(readerWorkCacheService.getCatalogPage(9L, 1, 20)).thenReturn(cached);

        AppPageVo<AppCatalogItemVo> result = service.getCatalogPage(9L, 1, 20);

        assertEquals(1L, result.getTotal());
        verifyNoInteractions(readerWorkMapper, readerNovelChapterMapper, readerComicChapterMapper);
    }

    @Test
    void getCatalogShouldQueryPublishedComicChaptersOrderedByChapterNo() {
        when(readerWorkCacheService.getCatalog(12L)).thenReturn(null);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.COMIC));
        when(readerComicChapterMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderComicChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(12L, 1, PublishStatus.PUBLISHED.name(), 1));
            assertOrderBy(wrapper, "chapter_no ASC");
            return List.of(
                comicChapter(301L, 12L, 1, "第一话", 3),
                comicChapter(302L, 12L, 2, "第二话", 4)
            );
        });

        List<AppCatalogItemVo> result = service.getCatalog(12L);

        assertEquals(2, result.size());
        assertEquals(301L, result.get(0).getChapterId());
        assertEquals("第一话", result.get(0).getChapterName());
        assertEquals(1, result.get(0).getChapterNo());
        assertEquals(302L, result.get(1).getChapterId());
        verify(readerWorkCacheService).cacheCatalog(12L, result);
    }

    @Test
    void getNovelChapterShouldReturnCachedChapterBeforeQueryingMysql() {
        AppNovelChapterVo cached = new AppNovelChapterVo();
        cached.setChapterId(201L);
        cached.setChapterName("缓存章节");
        when(readerNovelChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(novelChapter(201L, 1, "第一章", "第一卷"));
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(9L, WorkType.NOVEL));
        when(readerWorkCacheService.getNovelChapter(9L, 201L)).thenReturn(cached);

        AppNovelChapterVo result = service.getNovelChapter(201L);

        assertEquals(201L, result.getChapterId());
        assertEquals("缓存章节", result.getChapterName());
        verify(readerNovelChapterContentMapper, never()).selectById(any());
    }

    @Test
    void getNovelChapterWithWorkIdShouldReturnRedisBeforeQueryingMysql() {
        AppNovelChapterVo cached = new AppNovelChapterVo();
        cached.setChapterId(201L);
        cached.setChapterName("缓存章节");
        when(readerWorkCacheService.getNovelChapter(9L, 201L)).thenReturn(cached);

        AppNovelChapterVo result = service.getNovelChapter(9L, 201L);

        assertEquals("缓存章节", result.getChapterName());
        verifyNoInteractions(readerWorkMapper, readerNovelChapterMapper, readerNovelChapterContentMapper);
    }

    @Test
    void getCatalogShouldReturnCachedCatalogBeforeQueryingMysql() {
        List<AppCatalogItemVo> cached = List.of(new AppCatalogItemVo());
        cached.getFirst().setChapterId(501L);
        cached.getFirst().setChapterName("缓存章节");
        when(readerWorkCacheService.getCatalog(12L)).thenReturn(cached);

        List<AppCatalogItemVo> result = service.getCatalog(12L);

        assertEquals(1, result.size());
        assertEquals(501L, result.getFirst().getChapterId());
        assertEquals("缓存章节", result.getFirst().getChapterName());
        verifyNoInteractions(readerWorkMapper);
        verify(readerWorkCacheService, never()).cacheCatalog(any(Long.class), any());
    }

    @Test
    void getNovelChapterShouldRejectMissingOrUnpublishedChapter() {
        when(readerNovelChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderNovelChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(99L, 1, PublishStatus.PUBLISHED.name(), 1));
            return null;
        });

        ServiceException exception = assertThrows(ServiceException.class, () -> service.getNovelChapter(99L));

        assertEquals("章节不存在", exception.getMessage());
    }

    @Test
    void getNovelChapterShouldRequirePublishedWorkAfterFindingPublishedChapter() {
        when(readerNovelChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderNovelChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(201L, 1, PublishStatus.PUBLISHED.name(), 1));
            return novelChapter(201L, 3, "第三章", "正文");
        });
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderWork> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(9L, 1, PublishStatus.PUBLISHED.name(), 1));
            return null;
        });

        ServiceException exception = assertThrows(ServiceException.class, () -> service.getNovelChapter(201L));

        assertEquals("章节不存在", exception.getMessage());
    }

    @Test
    void getComicChapterShouldQueryPublishedChapterAndOrderedPages() {
        when(readerComicChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderComicChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(301L, 1, PublishStatus.PUBLISHED.name(), 1));
            return comicChapter(301L, 12L, 2, "第十二话", 2);
        });
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.COMIC));
        when(readerComicPageMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderComicPage> wrapper = invocation.getArgument(0);
            assertWrapperContainsParams(wrapper, Map.of(301L, 1));
            assertOrderBy(wrapper, "page_no ASC");
            return List.of(
                comicPage(1, "https://img/1.jpg"),
                comicPage(2, "https://img/2.jpg")
            );
        });

        AppComicChapterVo result = service.getComicChapter(301L);

        assertEquals(301L, result.getChapterId());
        assertEquals("第十二话", result.getChapterName());
        assertEquals(2, result.getPageCount());
        assertIterableEquals(List.of("https://img/1.jpg", "https://img/2.jpg"), result.getImageUrls());
    }

    @Test
    void getComicChapterShouldRequirePublishedWorkAfterFindingPublishedChapter() {
        when(readerComicChapterMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderComicChapter> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(401L, 1, PublishStatus.PUBLISHED.name(), 1));
            return comicChapter(401L, 18L, 6, "第六话", 2);
        });
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<ReaderWork> wrapper = invocation.getArgument(0);
            assertPublishedWrapper(wrapper, "publish_status", Map.of(18L, 1, PublishStatus.PUBLISHED.name(), 1));
            return null;
        });

        ServiceException exception = assertThrows(ServiceException.class, () -> service.getComicChapter(401L));

        assertEquals("章节不存在", exception.getMessage());
    }

    private static void initializeTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    private static void assertPublishedWrapper(LambdaQueryWrapper<?> wrapper, String publishFieldToken, Map<Object, Integer> expectedValues) {
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains(publishFieldToken));
        assertWrapperContainsParams(wrapper, expectedValues);
    }

    private static void assertOrderBy(LambdaQueryWrapper<?> wrapper, String orderToken) {
        assertTrue(wrapper.getSqlSegment().contains(orderToken));
    }

    private static void assertWrapperContainsParams(LambdaQueryWrapper<?> wrapper, Map<Object, Integer> expectedValues) {
        wrapper.getSqlSegment();
        Map<String, Object> paramPairs = wrapper.getParamNameValuePairs();
        expectedValues.forEach((expectedValue, expectedCount) -> {
            long actualCount = paramPairs.values().stream()
                .filter(actualValue -> valuesMatch(actualValue, expectedValue))
                .count();
            assertEquals(expectedCount.longValue(), actualCount);
        });
    }

    private static boolean valuesMatch(Object actualValue, Object expectedValue) {
        if (actualValue instanceof Number actualNumber && expectedValue instanceof Number expectedNumber) {
            return actualNumber.longValue() == expectedNumber.longValue();
        }
        if (expectedValue.equals(actualValue)) {
            return true;
        }
        return String.valueOf(expectedValue).equals(String.valueOf(actualValue));
    }

    private ReaderWork publishedWork(Long workId, WorkType workType) {
        ReaderWork work = new ReaderWork();
        work.setId(workId);
        work.setWorkType(workType.name());
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        return work;
    }

    private ReaderNovelChapter novelChapter(Long chapterId, Integer chapterNo, String chapterName, String volumeName) {
        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(chapterId);
        chapter.setWorkId(9L);
        chapter.setChapterNo(chapterNo);
        chapter.setChapterName(chapterName);
        chapter.setVolumeName(volumeName);
        chapter.setWordCount(1000);
        chapter.setPublishStatus(PublishStatus.PUBLISHED.name());
        return chapter;
    }

    private ReaderNovelChapterContent novelChapterContent(Long chapterId, String content) {
        ReaderNovelChapterContent chapterContent = new ReaderNovelChapterContent();
        chapterContent.setChapterId(chapterId);
        chapterContent.setContent(content);
        return chapterContent;
    }

    private ReaderComicChapter comicChapter(Long chapterId, Long workId, Integer chapterNo, String chapterName, Integer pageCount) {
        ReaderComicChapter chapter = new ReaderComicChapter();
        chapter.setId(chapterId);
        chapter.setWorkId(workId);
        chapter.setChapterNo(chapterNo);
        chapter.setChapterName(chapterName);
        chapter.setPageCount(pageCount);
        chapter.setPublishStatus(PublishStatus.PUBLISHED.name());
        return chapter;
    }

    private ReaderComicPage comicPage(Integer pageNo, String imageUrl) {
        ReaderComicPage page = new ReaderComicPage();
        page.setPageNo(pageNo);
        page.setImageUrl(imageUrl);
        return page;
    }
}
