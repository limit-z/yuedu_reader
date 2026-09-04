package org.dromara.reader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderReadingHistory;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.domain.vo.app.AppReadingProgressVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.service.cache.ReaderProgressCacheService;
import org.dromara.reader.service.impl.ReaderProgressServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderProgressServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderWork.class);
        initializeTableInfo(ReaderReadingProgress.class);
        initializeTableInfo(ReaderReadingHistory.class);
        initializeTableInfo(ReaderBookshelf.class);
        initializeTableInfo(ReaderNovelChapter.class);
    }

    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderReadingProgressMapper readingProgressMapper;
    @Mock
    private ReaderReadingHistoryMapper readingHistoryMapper;
    @Mock
    private ReaderBookshelfMapper bookshelfMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderComicChapterMapper comicChapterMapper;
    @Mock
    private ReaderProgressCacheService progressCacheService;
    @Mock
    private ReaderVisitorAccountService visitorAccountService;

    @InjectMocks
    private ReaderProgressServiceImpl service;

    @Test
    void saveProgressShouldUpsertCurrentUserProgressAndHistory() {
        ReaderProgressBo bo = new ReaderProgressBo();
        bo.setWorkId(12L);
        bo.setChapterId(201L);
        bo.setPageNo(6);
        bo.setReadPercent(80);
        bo.setClientType("H5");

        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(readingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(readingHistoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(bookshelfMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        service.saveProgress(bo);

        verify(readingProgressMapper).insert(any(ReaderReadingProgress.class));
        verify(readingHistoryMapper).insert(any(ReaderReadingHistory.class));
        verify(progressCacheService).cacheProgress(any(ReaderReadingProgress.class));
    }

    @Test
    void getProgressShouldPreferCachedProgressOverMysql() {
        ReaderReadingProgress progress = new ReaderReadingProgress();
        progress.setUserId(7L);
        progress.setWorkId(12L);
        progress.setChapterId(201L);
        progress.setLocationValue("6");
        progress.setProgressPercent(80);
        progress.setClientType("H5");
        progress.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 12, 30));

        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(201L);
        chapter.setChapterName("第十二章");
        chapter.setChapterNo(12);

        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(novelChapterMapper.selectById(201L)).thenReturn(chapter);
        when(progressCacheService.getProgress(7L, 12L)).thenReturn(progress);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        AppReadingProgressVo result = service.getProgress(12L);

        assertNotNull(result);
        assertEquals(12L, result.getWorkId());
        assertEquals(201L, result.getChapterId());
        assertEquals(6, result.getPageNo());
        assertEquals(80, result.getReadPercent());
        assertEquals("第十二章", result.getChapterName());
        verify(readingProgressMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void getProgressShouldFallbackToMysqlWhenCacheMiss() {
        ReaderReadingProgress progress = new ReaderReadingProgress();
        progress.setUserId(7L);
        progress.setWorkId(12L);
        progress.setChapterId(201L);
        progress.setLocationValue("9");
        progress.setProgressPercent(90);
        progress.setClientType("APP");
        progress.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 13, 0));

        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(201L);
        chapter.setChapterName("第十三章");
        chapter.setChapterNo(13);

        when(readingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(progress);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(novelChapterMapper.selectById(201L)).thenReturn(chapter);
        when(progressCacheService.getProgress(7L, 12L)).thenReturn(null);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        AppReadingProgressVo result = service.getProgress(12L);

        assertNotNull(result);
        assertEquals(9, result.getPageNo());
        assertEquals(90, result.getReadPercent());
        assertEquals("第十三章", result.getChapterName());
        verify(readingProgressMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(progressCacheService).cacheSnapshot(progress);
    }

    @Test
    void saveProgressShouldPreserveStructuredLocationWhenPageNumberIsAlsoProvided() {
        ReaderProgressBo bo = new ReaderProgressBo();
        bo.setWorkId(12L);
        bo.setChapterId(201L);
        bo.setPageNo(4);
        bo.setReadPercent(36);
        bo.setLocationValue("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860,\"turnMode\":\"仿真翻页\"}");
        bo.setClientType("H5");

        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(readingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(readingHistoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(bookshelfMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);

        service.saveProgress(bo);

        ArgumentCaptor<ReaderReadingProgress> progressCaptor = ArgumentCaptor.forClass(ReaderReadingProgress.class);
        verify(readingProgressMapper).insert(progressCaptor.capture());
        assertEquals(bo.getLocationValue(), progressCaptor.getValue().getLocationValue());
    }

    @Test
    void getProgressShouldReturnStructuredLocationAndCompatiblePageNumber() {
        ReaderReadingProgress progress = new ReaderReadingProgress();
        progress.setUserId(7L);
        progress.setWorkId(12L);
        progress.setChapterId(201L);
        progress.setLocationValue("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860,\"turnMode\":\"仿真翻页\"}");
        progress.setProgressPercent(36);
        progress.setClientType("H5");

        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(201L);
        chapter.setChapterName("第四章");
        chapter.setChapterNo(4);

        when(progressCacheService.getProgress(7L, 12L)).thenReturn(progress);
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(novelChapterMapper.selectById(201L)).thenReturn(chapter);
        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);

        AppReadingProgressVo result = service.getProgress(12L);

        assertEquals(4, result.getPageNo());
        assertEquals(progress.getLocationValue(), result.getLocationValue());
    }

    private ReaderWork publishedWork(Long workId, WorkType workType) {
        ReaderWork work = new ReaderWork();
        work.setId(workId);
        work.setWorkType(workType.name());
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        return work;
    }

    private static void initializeTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
