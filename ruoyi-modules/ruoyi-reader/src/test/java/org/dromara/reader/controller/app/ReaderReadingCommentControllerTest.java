package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.bo.ReaderReadingCommentSubmitBo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppReadingCommentVo;
import org.dromara.reader.service.IReaderReadingCommentService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderReadingCommentControllerTest {

    @Mock
    private IReaderReadingCommentService readingCommentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderReadingCommentController(readingCommentService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposeCommentPage() throws Exception {
        AppReadingCommentVo vo = new AppReadingCommentVo();
        vo.setCommentId(1L);
        vo.setNickName("悦读用户");
        vo.setCommentContent("很好看");
        AppPageVo<AppReadingCommentVo> pageVo = new AppPageVo<>();
        pageVo.setList(List.of(vo));
        pageVo.setTotal(1L);
        pageVo.setPageNum(1);
        pageVo.setPageSize(20);
        pageVo.setTotalPages(1);
        when(readingCommentService.listComments(1L, 2L, 1, 20)).thenReturn(pageVo);

        mockMvc.perform(get("/reader/app/reading/comments")
                .param("workId", "1")
                .param("chapterId", "2")
                .param("pageNum", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list[0].nickName").value("悦读用户"));
    }

    @Test
    void submitShouldExposeCommentId() throws Exception {
        when(readingCommentService.submitComment(org.mockito.ArgumentMatchers.any(ReaderReadingCommentSubmitBo.class)))
            .thenReturn(99L);

        mockMvc.perform(post("/reader/app/reading/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workId\":1,\"chapterId\":2,\"commentContent\":\"不错\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(99));
    }
}
