package org.dromara.reader.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderReadingHistory;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppBookshelfItemVo;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.service.impl.ReaderBookshelfServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderBookshelfServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderBookshelf.class);
        initializeTableInfo(ReaderWork.class);
        initializeTableInfo(ReaderReadingProgress.class);
        initializeTableInfo(ReaderReadingHistory.class);
        initializeTableInfo(ReaderNovelChapter.class);
    }

    @Mock
    private ReaderBookshelfMapper bookshelfMapper;
    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderReadingProgressMapper readingProgressMapper;
    @Mock
    private ReaderReadingHistoryMapper readingHistoryMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderComicChapterMapper comicChapterMapper;
    @Mock
    private ReaderVisitorAccountService visitorAccountService;

    @InjectMocks
    private ReaderBookshelfServiceImpl service;

    @Test
    void addShouldCreateBookshelfEntryForPublishedWork() {
        when(readerWorkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(publishedWork(12L, WorkType.NOVEL));
        when(bookshelfMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        service.add(12L);

        verify(bookshelfMapper).insert(any(ReaderBookshelf.class));
    }

    @Test
    void listBookshelfShouldMergeProgressForCurrentUser() {
        ReaderBookshelf bookshelf = new ReaderBookshelf();
        bookshelf.setUserId(7L);
        bookshelf.setWorkId(12L);
        bookshelf.setUpdateTime(LocalDateTime.of(2026, 8, 10, 11, 0));

        ReaderWork work = publishedWork(12L, WorkType.NOVEL);
        work.setTitle("三体");
        work.setCoverUrl("https://img/cover.jpg");

        ReaderReadingProgress progress = new ReaderReadingProgress();
        progress.setWorkId(12L);
        progress.setChapterId(201L);
        progress.setProgressPercent(66);

        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(201L);
        chapter.setChapterName("第十二章");

        when(bookshelfMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(bookshelf));
        when(readerWorkMapper.selectBatchIds(List.of(12L))).thenReturn(List.of(work));
        when(readingProgressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(progress));
        when(novelChapterMapper.selectById(201L)).thenReturn(chapter);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        List<AppBookshelfItemVo> result = service.listBookshelf();

        assertEquals(1, result.size());
        assertEquals(12L, result.getFirst().getWorkId());
        assertEquals("三体", result.getFirst().getTitle());
        assertEquals(201L, result.getFirst().getContinueChapterId());
        assertEquals("第十二章", result.getFirst().getContinueChapterName());
        assertEquals(66, result.getFirst().getContinueProgress());
    }

    @Test
    void listHistoryShouldExposeCurrentUserRecentHistory() {
        ReaderReadingHistory history = new ReaderReadingHistory();
        history.setId(91L);
        history.setUserId(7L);
        history.setWorkId(12L);
        history.setChapterId(201L);
        history.setContentType(WorkType.NOVEL.name());
        history.setLocationValue("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860}");
        history.setReadAt(LocalDateTime.of(2026, 8, 10, 12, 0));

        ReaderWork work = publishedWork(12L, WorkType.NOVEL);
        work.setTitle("灌篮高手");

        ReaderNovelChapter chapter = new ReaderNovelChapter();
        chapter.setId(201L);
        chapter.setChapterName("第十二章");
        chapter.setChapterNo(12);

        when(readingHistoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(history));
        when(readerWorkMapper.selectBatchIds(List.of(12L))).thenReturn(List.of(work));
        when(novelChapterMapper.selectById(201L)).thenReturn(chapter);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        List<AppHistoryItemVo> result = service.listHistory();

        assertEquals(1, result.size());
        assertEquals(91L, result.getFirst().getHistoryId());
        assertEquals("灌篮高手", result.getFirst().getTitle());
        assertEquals("第十二章", result.getFirst().getChapterName());
        assertEquals(12, result.getFirst().getChapterNo());
        assertEquals(4, result.getFirst().getPageNo());
        assertEquals("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860}", result.getFirst().getLocationValue());
    }

    @Test
    void setTopPinShouldUpdateTargetShelf() {
        ReaderBookshelf bookshelf = new ReaderBookshelf();
        bookshelf.setId(3L);
        bookshelf.setUserId(7L);
        bookshelf.setWorkId(12L);
        bookshelf.setTopPin("0");
        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        when(bookshelfMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bookshelf);

        service.setTopPin(12L, true);

        assertEquals("1", bookshelf.getTopPin());
        verify(bookshelfMapper).updateById(bookshelf);
    }

    @Test
    void reorderShouldPersistRequestedOrderAndKeepRemainingItems() {
        ReaderBookshelf first = new ReaderBookshelf();
        first.setId(3L);
        first.setUserId(7L);
        first.setWorkId(12L);
        first.setSortNo(3);

        ReaderBookshelf second = new ReaderBookshelf();
        second.setId(4L);
        second.setUserId(7L);
        second.setWorkId(13L);
        second.setSortNo(4);

        ReaderBookshelf third = new ReaderBookshelf();
        third.setId(5L);
        third.setUserId(7L);
        third.setWorkId(14L);
        third.setSortNo(5);

        when(visitorAccountService.requireCurrentReaderId()).thenReturn(7L);
        when(bookshelfMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second, third));

        service.reorder(List.of(14L, 12L));

        assertEquals(1, third.getSortNo());
        assertEquals(2, first.getSortNo());
        assertEquals(3, second.getSortNo());
        verify(bookshelfMapper).updateById(third);
        verify(bookshelfMapper).updateById(first);
        verify(bookshelfMapper).updateById(second);
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
