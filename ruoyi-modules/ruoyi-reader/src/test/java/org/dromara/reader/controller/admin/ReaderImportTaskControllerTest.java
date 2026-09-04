package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.admin.ReaderImportTaskAdminVo;
import org.dromara.reader.service.IReaderImportTaskService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderImportTaskControllerTest {

    @Mock
    private IReaderImportTaskService importTaskService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderImportTaskController(importTaskService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposePagedImportTaskPayload() throws Exception {
        ReaderImportTaskAdminVo taskVo = new ReaderImportTaskAdminVo();
        taskVo.setId(21L);
        taskVo.setTaskName("三体-epub");
        taskVo.setStatus("CREATED");
        when(importTaskService.queryPageList(any(), any())).thenReturn(PageResult.build(List.of(taskVo), 1));

        mockMvc.perform(get("/reader/admin/import/tasks/list").param("taskName", "三体").param("pageNum", "1").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0].id").value(21))
            .andExpect(jsonPath("$.data.rows[0].taskName").value("三体-epub"));
    }
}
