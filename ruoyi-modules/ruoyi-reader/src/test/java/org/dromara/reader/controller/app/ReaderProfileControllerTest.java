package org.dromara.reader.controller.app;

import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.dromara.reader.domain.vo.app.ReaderProfileVo;
import org.dromara.reader.service.IReaderPortalService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderProfileControllerTest {

    @Mock
    private IReaderPortalService portalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReaderProfileController(portalService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void meShouldExposeUnreadCount() throws Exception {
        ReaderProfileVo vo = new ReaderProfileVo();
        vo.setUserId(7L);
        vo.setUnreadMessageCount(3L);
        when(portalService.getProfile()).thenReturn(vo);

        mockMvc.perform(get("/reader/app/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.userId").value(7))
            .andExpect(jsonPath("$.data.unreadMessageCount").value(3));
    }

    @Test
    void updateMeShouldDelegateReaderContactProfile() throws Exception {
        mockMvc.perform(put("/reader/app/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nickName":"林默","mobile":"13800000000","email":"linmo@example.com","signature":"保持好奇"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portalService).updateProfile(any());
    }

    @Test
    void messagesShouldForwardFilters() throws Exception {
        mockMvc.perform(get("/reader/app/messages")
                .param("messageType", "UPDATE")
                .param("unreadOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portalService).listMessages(eq("UPDATE"), eq(Boolean.TRUE));
    }

    @Test
    void unreadCountShouldExposeServiceValue() throws Exception {
        when(portalService.countUnreadMessages()).thenReturn(5L);

        mockMvc.perform(get("/reader/app/messages/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void readAllShouldDelegateToService() throws Exception {
        mockMvc.perform(put("/reader/app/messages/read-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portalService).markAllMessagesRead();
    }

    @Test
    void deleteMessageShouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/reader/app/messages/{messageId}", 99L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portalService).removeMessage(99L);
    }

    @Test
    void deleteMessagesShouldDelegateBatchIds() throws Exception {
        mockMvc.perform(delete("/reader/app/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"messageIds":[99,100]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portalService).removeMessages(org.mockito.ArgumentMatchers.anyList());
    }
}
