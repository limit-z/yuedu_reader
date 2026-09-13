package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppRankingVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.service.IReaderRankingService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderRankingControllerTest {

    @Mock
    private IReaderRankingService rankingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderRankingController(rankingService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listRankingsShouldExposeTabs() throws Exception {
        AppRankingVo rankingVo = new AppRankingVo();
        rankingVo.setRankingKey("hot");
        rankingVo.setRankingName("畅销榜");
        when(rankingService.listRankings()).thenReturn(List.of(rankingVo));

        mockMvc.perform(get("/reader/app/rankings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].rankingKey").value("hot"))
            .andExpect(jsonPath("$.data[0].rankingName").value("畅销榜"));
    }

    @Test
    void listWorksShouldExposePage() throws Exception {
        AppWorkCardVo cardVo = new AppWorkCardVo();
        cardVo.setWorkId(1L);
        cardVo.setTitle("三体");
        cardVo.setSerialStatus("FINISHED");
        AppPageVo<AppWorkCardVo> pageVo = new AppPageVo<>();
        pageVo.setList(List.of(cardVo));
        pageVo.setTotal(1L);
        pageVo.setPageNum(1);
        pageVo.setPageSize(20);
        pageVo.setTotalPages(1);
        when(rankingService.listRankingWorks("hot", 1, 20)).thenReturn(pageVo);

        mockMvc.perform(get("/reader/app/rankings/{rankingKey}/works", "hot")
                .param("pageNum", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.list[0].title").value("三体"))
            .andExpect(jsonPath("$.data.list[0].serialStatus").value("FINISHED"));
    }
}
