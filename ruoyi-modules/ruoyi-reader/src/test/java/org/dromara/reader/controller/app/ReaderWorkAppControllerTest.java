package org.dromara.reader.controller.app;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.dromara.reader.service.IReaderAppContentService;
import org.dromara.reader.service.IReaderBookshelfService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderWorkAppControllerTest {

    @Mock
    private IReaderAppContentService appContentService;
    @Mock
    private IReaderBookshelfService bookshelfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderWorkAppController(appContentService, bookshelfService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void detailShouldExposeWorkPayloadOverHttp() throws Exception {
        AppWorkDetailVo detailVo = new AppWorkDetailVo();
        detailVo.setWorkId(100L);
        detailVo.setTitle("三体");
        detailVo.setWorkType("NOVEL");
        when(appContentService.getWorkDetail(100L)).thenReturn(detailVo);

        mockMvc.perform(get("/reader/app/works/{workId}", 100L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.workId").value(100))
            .andExpect(jsonPath("$.data.title").value("三体"))
            .andExpect(jsonPath("$.data.workType").value("NOVEL"));
    }

    @Test
    void catalogShouldExposeChapterListOverHttp() throws Exception {
        AppCatalogItemVo first = new AppCatalogItemVo();
        first.setChapterId(200L);
        first.setChapterName("第一章");
        first.setChapterNo(1);
        when(appContentService.getCatalog(100L)).thenReturn(List.of(first));

        mockMvc.perform(get("/reader/app/works/{workId}/catalog", 100L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].chapterId").value(200))
            .andExpect(jsonPath("$.data[0].chapterName").value("第一章"))
            .andExpect(jsonPath("$.data[0].chapterNo").value(1));
    }

    @Test
    void detailShouldUseGlobalExceptionHandlingForMissingWork() throws Exception {
        when(appContentService.getWorkDetail(999L)).thenThrow(new ServiceException("作品不存在"));

        mockMvc.perform(get("/reader/app/works/{workId}", 999L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("作品不存在"));
    }

    @Test
    void addToBookshelfShouldExposeSuccessOverHttp() throws Exception {
        mockMvc.perform(post("/reader/app/works/{workId}/bookshelf", 321L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).add(321L);
    }

    @Test
    void removeFromBookshelfShouldExposeSuccessOverHttp() throws Exception {
        mockMvc.perform(delete("/reader/app/works/{workId}/bookshelf", 321L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).remove(321L);
    }

    @Test
    void addToBookshelfShouldUseGlobalExceptionHandlingForMissingWork() throws Exception {
        doThrow(new ServiceException("作品不存在")).when(bookshelfService).add(404L);

        mockMvc.perform(post("/reader/app/works/{workId}/bookshelf", 404L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("作品不存在"));
    }
}
