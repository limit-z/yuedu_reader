package org.dromara.reader.service;

import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderComicPage;
import org.dromara.reader.domain.ReaderImportFile;
import org.dromara.reader.domain.ReaderImportTask;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderImportTaskBo;
import org.dromara.reader.enums.ImportTaskStatus;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderImportFileMapper;
import org.dromara.reader.mapper.ReaderImportTaskMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.impl.ReaderImportTaskServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
public class ReaderImportTaskServiceTest {

    @Mock
    private ReaderImportTaskMapper importTaskMapper;
    @Mock
    private ReaderImportFileMapper importFileMapper;
    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderNovelChapterContentMapper novelChapterContentMapper;
    @Mock
    private ReaderComicChapterMapper comicChapterMapper;
    @Mock
    private ReaderComicPageMapper comicPageMapper;
    @Mock
    private ReaderContentAuditMapper contentAuditMapper;
    @Mock
    private ISysOssService ossService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    private ReaderImportTaskServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        service = new ReaderImportTaskServiceImpl(
            importTaskMapper,
            importFileMapper,
            readerWorkMapper,
            novelChapterMapper,
            novelChapterContentMapper,
            comicChapterMapper,
            comicPageMapper,
            contentAuditMapper,
            ossService,
            transactionTemplate,
            scheduledExecutorService
        );
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(scheduledExecutorService).execute(any(Runnable.class));
    }

    @Test
    public void shouldUseCreatedAsFirstImportStatus() {
        assertEquals("CREATED", ImportTaskStatus.CREATED.name());
    }

    @Test
    public void createTaskShouldGenerateNovelDraftAndPendingAudit() {
        ReaderImportTaskBo bo = new ReaderImportTaskBo();
        bo.setTaskName("三体 TXT 导入");
        bo.setContentType("NOVEL");
        bo.setCategoryName("科幻");
        bo.setOssId(9L);
        bo.setCoverOssId(99L);

        SysOssVo oss = new SysOssVo();
        oss.setOssId(9L);
        oss.setOriginalName("three-body.txt");
        oss.setFileSuffix("txt");
        oss.setUrl("https://oss.example.com/three-body.txt");

        SysOssVo cover = new SysOssVo();
        cover.setOssId(99L);
        cover.setOriginalName("three-body-cover.png");
        cover.setFileSuffix("png");
        cover.setUrl("https://oss.example.com/three-body-cover.png");

        when(ossService.getById(9L)).thenReturn(oss);
        when(ossService.getById(99L)).thenReturn(cover);
        when(ossService.download(9L)).thenReturn(ResponseEntity.ok("第一章 宇宙闪烁\n宇宙闪烁在远方的夜空中持续了很久。".getBytes(StandardCharsets.UTF_8)));

        doAnswer(invocation -> {
            ReaderImportTask task = invocation.getArgument(0);
            task.setId(10L);
            return 1;
        }).when(importTaskMapper).insert(any(ReaderImportTask.class));

        AtomicReference<ReaderWork> storedWork = new AtomicReference<>();
        doAnswer(invocation -> {
            ReaderWork work = invocation.getArgument(0);
            work.setId(20L);
            storedWork.set(work);
            return 1;
        }).when(readerWorkMapper).insert(any(ReaderWork.class));
        Long taskId = service.createTask(bo);

        assertEquals(10L, taskId);
        assertEquals("科幻", storedWork.get().getCategoryName());
        assertEquals("https://oss.example.com/three-body-cover.png", storedWork.get().getCoverUrl());

        ArgumentCaptor<ReaderImportFile> importFileCaptor = ArgumentCaptor.forClass(ReaderImportFile.class);
        verify(importFileMapper).insert(importFileCaptor.capture());
        assertEquals(10L, importFileCaptor.getValue().getTaskId());
        assertEquals("txt", importFileCaptor.getValue().getFileSuffix());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderNovelChapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(novelChapterMapper).insertBatch(chapterCaptor.capture(), anyInt());
        ReaderNovelChapter storedChapter = chapterCaptor.getValue().get(0);
        assertEquals(20L, storedChapter.getWorkId());
        assertEquals("第一章 宇宙闪烁", storedChapter.getChapterName());
        assertEquals(1, storedChapter.getChapterNo());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderNovelChapterContent>> contentCaptor = ArgumentCaptor.forClass(List.class);
        verify(novelChapterContentMapper).insertBatch(contentCaptor.capture(), anyInt());
        assertTrue(contentCaptor.getValue().get(0).getContent().contains("宇宙闪烁在远方"));

        ArgumentCaptor<ReaderContentAudit> auditCaptor = ArgumentCaptor.forClass(ReaderContentAudit.class);
        verify(contentAuditMapper).insert(auditCaptor.capture());
        assertEquals(20L, auditCaptor.getValue().getWorkId());
        assertEquals("PENDING", auditCaptor.getValue().getAuditStatus());

        verify(readerWorkMapper).updateById(any(ReaderWork.class));
        verify(comicChapterMapper, never()).insert(any(ReaderComicChapter.class));
        verify(comicPageMapper, never()).insert(any(ReaderComicPage.class));
        ArgumentCaptor<ReaderImportTask> taskCaptor = ArgumentCaptor.forClass(ReaderImportTask.class);
        verify(importTaskMapper, org.mockito.Mockito.atLeastOnce()).updateById(taskCaptor.capture());
        List<ReaderImportTask> tasks = taskCaptor.getAllValues();
        assertEquals(ImportTaskStatus.PENDING_REVIEW.name(), tasks.get(tasks.size() - 1).getStatus());
    }

    @Test
    public void createTaskShouldDecodeGb18030TxtContent() {
        ReaderImportTaskBo bo = new ReaderImportTaskBo();
        bo.setTaskName("修行者导入");
        bo.setContentType("NOVEL");
        bo.setOssId(9L);

        SysOssVo oss = new SysOssVo();
        oss.setOssId(9L);
        oss.setOriginalName("reader.txt");
        oss.setFileSuffix("txt");
        oss.setUrl("https://oss.example.com/reader.txt");

        when(ossService.getById(9L)).thenReturn(oss);
        when(ossService.download(9L)).thenReturn(ResponseEntity.ok("谁还不是个修行者了".getBytes(Charset.forName("GB18030"))));

        doAnswer(invocation -> {
            ReaderImportTask task = invocation.getArgument(0);
            task.setId(11L);
            return 1;
        }).when(importTaskMapper).insert(any(ReaderImportTask.class));

        AtomicReference<ReaderWork> storedWork = new AtomicReference<>();
        doAnswer(invocation -> {
            ReaderWork work = invocation.getArgument(0);
            work.setId(21L);
            storedWork.set(work);
            return 1;
        }).when(readerWorkMapper).insert(any(ReaderWork.class));
        service.createTask(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderNovelChapterContent>> contentCaptor = ArgumentCaptor.forClass(List.class);
        verify(novelChapterContentMapper).insertBatch(contentCaptor.capture(), anyInt());
        assertTrue(contentCaptor.getValue().get(0).getContent().contains("谁还不是个修行者了"));
    }

    @Test
    public void createTaskShouldSplitTxtIntoMultipleChapters() {
        ReaderImportTaskBo bo = new ReaderImportTaskBo();
        bo.setTaskName("自动分章测试");
        bo.setContentType("NOVEL");
        bo.setOssId(19L);

        SysOssVo oss = new SysOssVo();
        oss.setOssId(19L);
        oss.setOriginalName("novel.txt");
        oss.setFileSuffix("txt");
        oss.setUrl("https://oss.example.com/novel.txt");

        String content = """
            第一章 星夜降临
            宇宙在这一夜闪烁。

            第二章 红岸来信
            叶文洁收到了新的消息。
            """;

        when(ossService.getById(19L)).thenReturn(oss);
        when(ossService.download(19L)).thenReturn(ResponseEntity.ok(content.getBytes(StandardCharsets.UTF_8)));

        doAnswer(invocation -> {
            ReaderImportTask task = invocation.getArgument(0);
            task.setId(13L);
            return 1;
        }).when(importTaskMapper).insert(any(ReaderImportTask.class));

        AtomicReference<ReaderWork> storedWork = new AtomicReference<>();
        doAnswer(invocation -> {
            ReaderWork work = invocation.getArgument(0);
            work.setId(23L);
            storedWork.set(work);
            return 1;
        }).when(readerWorkMapper).insert(any(ReaderWork.class));
        service.createTask(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderNovelChapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(novelChapterMapper).insertBatch(chapterCaptor.capture(), anyInt());
        List<ReaderNovelChapter> chapters = chapterCaptor.getValue();
        assertEquals(2, chapters.size());
        assertEquals("第一章 星夜降临", chapters.get(0).getChapterName());
        assertEquals("第二章 红岸来信", chapters.get(1).getChapterName());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderNovelChapterContent>> contentCaptor = ArgumentCaptor.forClass(List.class);
        verify(novelChapterContentMapper).insertBatch(contentCaptor.capture(), anyInt());
        List<ReaderNovelChapterContent> chapterContents = contentCaptor.getValue();
        assertEquals(2, chapterContents.size());
        assertTrue(chapterContents.get(0).getContent().contains("宇宙在这一夜闪烁"));
        assertTrue(chapterContents.get(1).getContent().contains("叶文洁收到了新的消息"));

        ArgumentCaptor<ReaderWork> workCaptor = ArgumentCaptor.forClass(ReaderWork.class);
        verify(readerWorkMapper).updateById(workCaptor.capture());
        assertEquals(2, workCaptor.getValue().getTotalChapters());
    }

    @Test
    public void createTaskShouldMarkTaskFailedWhenParsingThrows() {
        ReaderImportTaskBo bo = new ReaderImportTaskBo();
        bo.setTaskName("失败导入");
        bo.setContentType("NOVEL");
        bo.setOssId(9L);

        SysOssVo oss = new SysOssVo();
        oss.setOssId(9L);
        oss.setOriginalName("broken.txt");
        oss.setFileSuffix("txt");
        oss.setUrl("https://oss.example.com/broken.txt");

        when(ossService.getById(9L)).thenReturn(oss);
        when(ossService.download(9L)).thenReturn(ResponseEntity.ok("正常内容".getBytes(StandardCharsets.UTF_8)));

        doAnswer(invocation -> {
            ReaderImportTask task = invocation.getArgument(0);
            task.setId(12L);
            return 1;
        }).when(importTaskMapper).insert(any(ReaderImportTask.class));

        doThrow(new RuntimeException("db write failed")).when(novelChapterMapper).insertBatch(any(), anyInt());

        service.createTask(bo);

        ArgumentCaptor<ReaderImportTask> taskCaptor = ArgumentCaptor.forClass(ReaderImportTask.class);
        verify(importTaskMapper, org.mockito.Mockito.atLeastOnce()).updateById(taskCaptor.capture());
        List<ReaderImportTask> tasks = taskCaptor.getAllValues();
        ReaderImportTask finalTask = tasks.get(tasks.size() - 1);
        assertEquals(12L, finalTask.getId());
        assertEquals(ImportTaskStatus.PARSE_FAILED.name(), finalTask.getStatus());
        assertTrue(finalTask.getFailReason().contains("db write failed"));
        verify(contentAuditMapper, never()).insert(any(ReaderContentAudit.class));
    }

    @Test
    public void createTaskShouldBatchInsertComicPages() {
        ReaderImportTaskBo bo = new ReaderImportTaskBo();
        bo.setTaskName("漫画批量导入");
        bo.setContentType("COMIC");
        bo.setOssId(29L);

        SysOssVo oss = new SysOssVo();
        oss.setOssId(29L);
        oss.setOriginalName("comic.zip");
        oss.setFileSuffix("zip");
        oss.setUrl("https://oss.example.com/comic.zip");

        byte[] emptyZip = new byte[] {80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        when(ossService.getById(29L)).thenReturn(oss);
        when(ossService.download(29L)).thenReturn(ResponseEntity.ok(emptyZip));

        doAnswer(invocation -> {
            ReaderImportTask task = invocation.getArgument(0);
            task.setId(14L);
            return 1;
        }).when(importTaskMapper).insert(any(ReaderImportTask.class));

        AtomicReference<ReaderWork> storedWork = new AtomicReference<>();
        doAnswer(invocation -> {
            ReaderWork work = invocation.getArgument(0);
            work.setId(24L);
            storedWork.set(work);
            return 1;
        }).when(readerWorkMapper).insert(any(ReaderWork.class));
        when(readerWorkMapper.selectById(24L)).thenAnswer(invocation -> storedWork.get());

        doAnswer(invocation -> {
            ReaderComicChapter chapter = invocation.getArgument(0);
            chapter.setId(34L);
            return 1;
        }).when(comicChapterMapper).insert(any(ReaderComicChapter.class));

        service.createTask(bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReaderComicPage>> pageCaptor = ArgumentCaptor.forClass(List.class);
        verify(comicPageMapper).insertBatch(pageCaptor.capture(), anyInt());
        List<ReaderComicPage> pages = pageCaptor.getValue();
        assertEquals(1, pages.size());
        assertEquals(34L, pages.get(0).getChapterId());
        assertEquals("https://oss.example.com/comic.zip", pages.get(0).getImageUrl());
    }
}
