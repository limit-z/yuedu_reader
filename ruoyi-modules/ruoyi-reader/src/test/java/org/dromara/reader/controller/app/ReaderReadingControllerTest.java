package org.dromara.reader.controller.app;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.domain.vo.app.AppReadingProgressVo;
import org.dromara.reader.service.IReaderAppContentService;
import org.dromara.reader.service.IReaderProgressService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderReadingControllerTest {

    @Mock
    private IReaderAppContentService appContentService;
    @Mock
    private IReaderProgressService progressService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderReadingController(appContentService, progressService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void novelChapterShouldExposePayloadOverHttp() throws Exception {
        AppNovelChapterVo chapterVo = new AppNovelChapterVo();
        chapterVo.setChapterId(11L);
        chapterVo.setChapterName("第二章");
        chapterVo.setContent("chapter-content");
        when(appContentService.getNovelChapter(null, 11L)).thenReturn(chapterVo);

        mockMvc.perform(get("/reader/app/reading/novels/{chapterId}", 11L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.chapterId").value(11))
            .andExpect(jsonPath("$.data.chapterName").value("第二章"))
            .andExpect(jsonPath("$.data.content").value("chapter-content"));
    }

    @Test
    void comicChapterShouldExposeOrderedImageUrlsOverHttp() throws Exception {
        AppComicChapterVo chapterVo = new AppComicChapterVo();
        chapterVo.setChapterId(22L);
        chapterVo.setImageUrls(List.of("https://img/1.jpg", "https://img/2.jpg"));
        when(appContentService.getComicChapter(null, 22L)).thenReturn(chapterVo);

        mockMvc.perform(get("/reader/app/reading/comics/{chapterId}", 22L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.chapterId").value(22))
            .andExpect(jsonPath("$.data.imageUrls[0]").value("https://img/1.jpg"))
            .andExpect(jsonPath("$.data.imageUrls[1]").value("https://img/2.jpg"));
    }

    @Test
    void novelChapterShouldUseGlobalExceptionHandlingForMissingContent() throws Exception {
        when(appContentService.getNovelChapter(null, 404L)).thenThrow(new ServiceException("章节不存在"));

        mockMvc.perform(get("/reader/app/reading/novels/{chapterId}", 404L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("章节不存在"));
    }

    @Test
    void novelChapterShouldPassWorkIdForDirectRedisLookup() throws Exception {
        AppNovelChapterVo chapterVo = new AppNovelChapterVo();
        chapterVo.setChapterId(11L);
        when(appContentService.getNovelChapter(9L, 11L)).thenReturn(chapterVo);

        mockMvc.perform(get("/reader/app/reading/novels/{chapterId}", 11L)
                .queryParam("workId", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chapterId").value(11));

        verify(appContentService).getNovelChapter(9L, 11L);
    }

    @Test
    void saveProgressShouldExposeSuccessOverHttp() throws Exception {
        mockMvc.perform(post("/reader/app/reading/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"workId":12,"chapterId":201,"pageNo":6,"readPercent":80,"clientType":"H5"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(progressService).saveProgress(any(ReaderProgressBo.class));
    }

    @Test
    void getProgressShouldExposePayloadOverHttp() throws Exception {
        AppReadingProgressVo progressVo = new AppReadingProgressVo();
        progressVo.setWorkId(12L);
        progressVo.setChapterId(201L);
        progressVo.setPageNo(6);
        progressVo.setReadPercent(80);
        when(progressService.getProgress(12L)).thenReturn(progressVo);

        mockMvc.perform(get("/reader/app/reading/progress/{workId}", 12L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.workId").value(12))
            .andExpect(jsonPath("$.data.chapterId").value(201))
            .andExpect(jsonPath("$.data.pageNo").value(6))
            .andExpect(jsonPath("$.data.readPercent").value(80));
    }
}
