package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppReadingStatsVo;
import org.dromara.reader.service.IReaderReadingStatsService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderReadingStatsControllerTest {

    @Mock
    private IReaderReadingStatsService readingStatsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderReadingStatsController(readingStatsService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void statsShouldExposeSummary() throws Exception {
        AppReadingStatsVo vo = new AppReadingStatsVo();
        vo.setMonthReadBooks(12);
        vo.setTotalReadBooks(42);
        vo.setFinishedBooks(18);
        when(readingStatsService.getStats()).thenReturn(vo);

        mockMvc.perform(get("/reader/app/reading/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.monthReadBooks").value(12))
            .andExpect(jsonPath("$.data.totalReadBooks").value(42));
    }
}
