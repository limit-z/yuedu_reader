package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.ReaderWorkVo;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.service.IReaderWorkService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderWorkControllerTest {

    @Mock
    private IReaderWorkService readerWorkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderWorkController(readerWorkService))
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
}
