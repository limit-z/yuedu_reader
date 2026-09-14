package org.dromara.reader.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.service.cache.ReaderRedisCacheClient;
import org.dromara.reader.service.impl.ReaderPointsServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ReaderAllEnvTest
class ReaderPointsServiceTest {

    @Test
    void guestCannotClaimDailyCheckin() {
        ReaderVisitorAccountService visitorAccountService = mock(ReaderVisitorAccountService.class);
        ReaderRedisCacheClient redisCacheClient = mock(ReaderRedisCacheClient.class);
        ReaderPointsServiceImpl service = new ReaderPointsServiceImpl(visitorAccountService, redisCacheClient);
        when(visitorAccountService.requireLoggedInReaderId())
            .thenThrow(new ServiceException("请先登录后再进行此操作"));

        assertThrows(ServiceException.class, service::claimDailyCheckin);
        verifyNoInteractions(redisCacheClient);
    }

    @Test
    void guestCannotClaimPointTask() {
        ReaderVisitorAccountService visitorAccountService = mock(ReaderVisitorAccountService.class);
        ReaderRedisCacheClient redisCacheClient = mock(ReaderRedisCacheClient.class);
        ReaderPointsServiceImpl service = new ReaderPointsServiceImpl(visitorAccountService, redisCacheClient);
        when(visitorAccountService.requireLoggedInReaderId())
            .thenThrow(new ServiceException("请先登录后再进行此操作"));

        assertThrows(ServiceException.class, () -> service.claimTask("read"));
        verifyNoInteractions(redisCacheClient);
    }
}
