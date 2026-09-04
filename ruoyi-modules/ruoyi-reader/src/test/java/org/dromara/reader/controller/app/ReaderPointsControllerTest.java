package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppPointsVo;
import org.dromara.reader.service.IReaderPointsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderPointsControllerTest {

    @Mock
    private IReaderPointsService pointsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderPointsController(pointsService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void dashboardShouldExposePointsVo() throws Exception {
        AppPointsVo vo = new AppPointsVo();
        vo.setTotalPoints(3280);
        vo.setTodayPoints(36);
        vo.setDays(15);
        vo.setClaimed(Boolean.FALSE);
        when(pointsService.getDashboard()).thenReturn(vo);

        mockMvc.perform(get("/reader/app/points"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.totalPoints").value(3280))
            .andExpect(jsonPath("$.data.todayPoints").value(36))
            .andExpect(jsonPath("$.data.days").value(15));
    }

    @Test
    void checkinShouldExposeUpdatedPoints() throws Exception {
        AppPointsVo vo = new AppPointsVo();
        vo.setClaimed(Boolean.TRUE);
        when(pointsService.claimDailyCheckin()).thenReturn(vo);

        mockMvc.perform(post("/reader/app/points/checkin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.claimed").value(true));
    }
}
