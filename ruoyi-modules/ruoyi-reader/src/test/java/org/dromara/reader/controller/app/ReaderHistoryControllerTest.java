package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderHistoryControllerTest {

    @Mock
    private IReaderBookshelfService bookshelfService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderHistoryController(bookshelfService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listShouldExposeReadingHistoryOverHttp() throws Exception {
        AppHistoryItemVo itemVo = new AppHistoryItemVo();
        itemVo.setHistoryId(91L);
        itemVo.setWorkId(12L);
        itemVo.setTitle("灌篮高手");
        itemVo.setChapterName("第十二话");
        itemVo.setChapterNo(12);
        itemVo.setPageNo(4);
        itemVo.setLocationValue("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860}");
        when(bookshelfService.listHistory()).thenReturn(List.of(itemVo));

        mockMvc.perform(get("/reader/app/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].historyId").value(91))
            .andExpect(jsonPath("$.data[0].title").value("灌篮高手"))
            .andExpect(jsonPath("$.data[0].chapterName").value("第十二话"))
            .andExpect(jsonPath("$.data[0].chapterNo").value(12))
            .andExpect(jsonPath("$.data[0].pageNo").value(4))
            .andExpect(jsonPath("$.data[0].locationValue").value("{\"pageNo\":4,\"pageIndex\":3,\"contentOffset\":1860}"));
    }

    @Test
    void removeShouldDelegateHistoryId() throws Exception {
        mockMvc.perform(delete("/reader/app/history/{historyId}", 91L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).removeHistory(91L);
    }

    @Test
    void clearShouldDelegateCurrentUserHistory() throws Exception {
        mockMvc.perform(delete("/reader/app/history/clear"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookshelfService).clearHistory();
    }
}
