package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppBookshelfItemVo;
import org.dromara.reader.service.IReaderBookshelfService;
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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderBookshelfControllerTest {

    @Mock
    private IReaderBookshelfService bookshelfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderBookshelfController(bookshelfService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposeBookshelfItemsOverHttp() throws Exception {
        AppBookshelfItemVo itemVo = new AppBookshelfItemVo();
        itemVo.setWorkId(12L);
        itemVo.setTitle("三体");
        itemVo.setWorkType("NOVEL");
        itemVo.setContinueChapterId(201L);
        itemVo.setContinueProgress(66);
        when(bookshelfService.listBookshelf()).thenReturn(List.of(itemVo));

        mockMvc.perform(get("/reader/app/bookshelf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].workId").value(12))
            .andExpect(jsonPath("$.data[0].title").value("三体"))
            .andExpect(jsonPath("$.data[0].continueProgress").value(66));
    }

    @Test
    void batchRemoveShouldDelegateRequestedWorkIds() throws Exception {
        mockMvc.perform(post("/reader/app/bookshelf/batch-remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"workIds":[12,13]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).batchRemove(anyList());
    }

    @Test
    void pinShouldDelegatePinnedState() throws Exception {
        mockMvc.perform(put("/reader/app/bookshelf/{workId}/pin", 12L)
                .param("pinned", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).setTopPin(12L, false);
    }

    @Test
    void reorderShouldDelegateRequestedWorkIds() throws Exception {
        mockMvc.perform(post("/reader/app/bookshelf/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"workIds":[12,13]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).reorder(anyList());
    }
}
