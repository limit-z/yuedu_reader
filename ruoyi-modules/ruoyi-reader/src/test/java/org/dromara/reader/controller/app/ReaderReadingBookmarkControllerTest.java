package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.bo.ReaderReadingBookmarkSaveBo;
import org.dromara.reader.domain.vo.app.AppReadingBookmarkVo;
import org.dromara.reader.service.IReaderReadingBookmarkService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderReadingBookmarkControllerTest {

    @Mock
    private IReaderReadingBookmarkService bookmarkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderReadingBookmarkController(bookmarkService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposeBookmarks() throws Exception {
        AppReadingBookmarkVo vo = new AppReadingBookmarkVo();
        vo.setBookmarkId(1L);
        vo.setWorkTitle("三体");
        when(bookmarkService.listBookmarks(1L)).thenReturn(List.of(vo));

        mockMvc.perform(get("/reader/app/reading/bookmarks").param("workId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].workTitle").value("三体"));
    }

    @Test
    void saveShouldExposeBookmarkId() throws Exception {
        when(bookmarkService.saveBookmark(any(ReaderReadingBookmarkSaveBo.class))).thenReturn(88L);

        mockMvc.perform(post("/reader/app/reading/bookmarks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workId\":1,\"chapterId\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(88));
    }

    @Test
    void removeShouldExposeSuccess() throws Exception {
        mockMvc.perform(delete("/reader/app/reading/bookmarks/{bookmarkId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
