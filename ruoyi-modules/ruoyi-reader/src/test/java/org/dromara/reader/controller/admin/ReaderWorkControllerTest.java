package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.ReaderWorkVo;
import org.dromara.reader.domain.vo.admin.ReaderCoverStyleVo;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.service.IReaderWorkService;
import org.dromara.reader.service.impl.ReaderCoverCrawlerService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderWorkControllerTest {

    @Mock
    private IReaderWorkService readerWorkService;

    @Mock
    private ReaderCoverCrawlerService readerCoverCrawlerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderWorkController(readerWorkService, readerCoverCrawlerService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposePagedWorkPayload() throws Exception {
        ReaderWorkVo workVo = new ReaderWorkVo();
        workVo.setId(11L);
        workVo.setTitle("三体");
        workVo.setWorkType("NOVEL");
        workVo.setPublishStatus("PUBLISHED");
        when(readerWorkService.queryPageList(any(), any())).thenReturn(PageResult.build(List.of(workVo), 1));

        mockMvc.perform(get("/reader/admin/works/list").param("keyword", "三体").param("pageNum", "1").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0].id").value(11))
            .andExpect(jsonPath("$.data.rows[0].title").value("三体"));
    }

    @Test
    void detailShouldExposeWorkPayload() throws Exception {
        ReaderWorkDetailAdminVo detailVo = new ReaderWorkDetailAdminVo();
        detailVo.setId(11L);
        detailVo.setTitle("三体");
        detailVo.setWorkType("NOVEL");
        detailVo.setPublishStatus("PUBLISHED");
        when(readerWorkService.queryDetail(11L)).thenReturn(detailVo);

        mockMvc.perform(get("/reader/admin/works/{workId}", 11L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(11))
            .andExpect(jsonPath("$.data.title").value("三体"));
    }

    @Test
    void publishShouldInvokeService() throws Exception {
        mockMvc.perform(put("/reader/admin/works/{workId}/publish", 11L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(readerWorkService).publish(11L);
    }

    @Test
    void offlineShouldInvokeService() throws Exception {
        mockMvc.perform(put("/reader/admin/works/{workId}/offline", 11L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(readerWorkService).offline(11L);
    }

    @Test
    void backfillCoversShouldExposeGeneratedCount() throws Exception {
        when(readerWorkService.backfillMissingCovers()).thenReturn(10);

        mockMvc.perform(post("/reader/admin/works/covers/backfill"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(10));

        verify(readerWorkService).backfillMissingCovers();
    }

    @Test
    void coverSettingsShouldExposeGlobalStyle() throws Exception {
        ReaderCoverStyleVo style = new ReaderCoverStyleVo();
        style.setMode("COLOR");
        style.setColor("#FFF4F2");
        when(readerWorkService.queryGlobalCoverStyle()).thenReturn(style);

        mockMvc.perform(get("/reader/admin/works/cover-settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("COLOR"))
            .andExpect(jsonPath("$.data.color").value("#FFF4F2"));
    }

    @Test
    void updateGlobalCoverSettingsShouldInvokeService() throws Exception {
        when(readerWorkService.updateGlobalCoverStyle(any())).thenReturn(6);

        mockMvc.perform(put("/reader/admin/works/cover-settings")
                .contentType(APPLICATION_JSON)
                .content("{\"mode\":\"COLOR\",\"color\":\"#EEF9F3\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(6));

        verify(readerWorkService).updateGlobalCoverStyle(argThat(bo ->
            "COLOR".equals(bo.getMode()) && "#EEF9F3".equals(bo.getColor())));
    }

    @Test
    void updateWorkCoverSettingsShouldInvokeService() throws Exception {
        mockMvc.perform(put("/reader/admin/works/{workId}/cover-settings", 11L)
                .contentType(APPLICATION_JSON)
                .content("{\"mode\":\"GLOBAL\"}"))
            .andExpect(status().isOk());

        verify(readerWorkService).updateWorkCoverStyle(eq(11L), argThat(bo -> "GLOBAL".equals(bo.getMode())));
    }

    @Test
    void reformatNovelContentsShouldExposeChangedCount() throws Exception {
        when(readerWorkService.reformatNovelContents()).thenReturn(20);

        mockMvc.perform(post("/reader/admin/works/chapters/reformat"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(20));

        verify(readerWorkService).reformatNovelContents();
    }
}
