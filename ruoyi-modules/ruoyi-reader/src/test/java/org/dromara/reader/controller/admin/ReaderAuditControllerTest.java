package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.admin.ReaderAuditRecordVo;
import org.dromara.reader.service.IReaderAuditService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderAuditControllerTest {

    @Mock
    private IReaderAuditService readerAuditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderAuditController(readerAuditService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposePagedAuditPayload() throws Exception {
        ReaderAuditRecordVo recordVo = new ReaderAuditRecordVo();
        recordVo.setId(31L);
        recordVo.setWorkId(11L);
        recordVo.setWorkTitle("三体");
        recordVo.setAuditStatus("PENDING");
        when(readerAuditService.queryPageList(any(), any())).thenReturn(PageResult.build(List.of(recordVo), 1));

        mockMvc.perform(get("/reader/admin/audits/list").param("workTitle", "三体").param("pageNum", "1").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0].id").value(31))
            .andExpect(jsonPath("$.data.rows[0].workTitle").value("三体"));
    }

    @Test
    void approveShouldInvokeService() throws Exception {
        mockMvc.perform(post("/reader/admin/audits/{auditId}/approve", 31L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(readerAuditService).approve(31L);
    }
}
