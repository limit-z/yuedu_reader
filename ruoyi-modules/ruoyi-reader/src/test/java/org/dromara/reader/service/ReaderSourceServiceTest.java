package org.dromara.reader.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.service.impl.ReaderSourceServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderSourceServiceTest {

    @Mock
    private ReaderSourceSiteMapper siteMapper;
    @Mock
    private ReaderSourcePolicyMapper policyMapper;
    @Mock
    private ReaderSourceRuleMapper ruleMapper;
    @Mock
    private ReaderSourceTaskMapper taskMapper;
    @Mock
    private ReaderSourceTaskRunMapper taskRunMapper;

    @InjectMocks
    private ReaderSourceServiceImpl service;

    @Test
    void saveSiteShouldRejectPrivateAddress() {
        ReaderSourceSiteBo bo = new ReaderSourceSiteBo();
        bo.setSiteName("本地站点");
        bo.setBaseUrl("http://127.0.0.1");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.saveSite(bo));

        assertEquals("站点地址不允许访问内网、保留地址或云元数据地址", exception.getMessage());
    }

    @Test
    void siteShouldNotBeEnabledBeforeComplianceApproval() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(3L);
        site.setComplianceStatus("UNCONFIRMED");
        when(siteMapper.selectById(3L)).thenReturn(site);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.updateSiteStatus(3L, true));

        assertEquals("站点尚未通过合规确认，不能启用", exception.getMessage());
    }

    @Test
    void policyShouldUseConservativeDefaultsWhenOptionalLimitsAreMissing() {
        ReaderSourcePolicyBo bo = new ReaderSourcePolicyBo();
        bo.setPolicyName("默认策略");

        service.savePolicy(bo);

        ArgumentCaptor<ReaderSourcePolicy> captor = ArgumentCaptor.forClass(ReaderSourcePolicy.class);
        verify(policyMapper).insert(captor.capture());
        ReaderSourcePolicy policy = captor.getValue();
        assertEquals(1, policy.getConcurrencyLimit());
        assertEquals(3000, policy.getMinDelayMs());
        assertEquals(8000, policy.getMaxDelayMs());
        assertEquals(10, policy.getRequestsPerMinute());
        assertEquals(1000, policy.getDailyRequestLimit());
        assertEquals("1", policy.getHonorRetryAfter());
    }

    @Test
    void ruleShouldRejectExecutableSelectorContent() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(7L);
        ReaderSourceRuleBo bo = new ReaderSourceRuleBo();
        bo.setSiteId(7L);
        bo.setRuleName("不安全规则");
        bo.setSelectorJson("{\"content\": \"javascript:alert(1)\"}");
        when(siteMapper.selectById(7L)).thenReturn(site);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.saveRule(bo));

        assertEquals("解析规则不允许脚本、javascript 或 eval 内容", exception.getMessage());
    }

    @Test
    void startTaskShouldCreateRunAndMoveTaskToRunning() {
        ReaderSourceTask task = new ReaderSourceTask();
        task.setId(12L);
        task.setStatus("DRAFT");
        task.setExecutorType("PYTHON");
        when(taskMapper.selectById(12L)).thenReturn(task);

        service.startTask(12L);

        ArgumentCaptor<ReaderSourceTaskRun> captor = ArgumentCaptor.forClass(ReaderSourceTaskRun.class);
        verify(taskRunMapper).insert(captor.capture());
        assertEquals(12L, captor.getValue().getTaskId());
        assertEquals("PYTHON", captor.getValue().getExecutorType());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertEquals("RUNNING", task.getStatus());
        verify(taskMapper).updateById(task);
    }
}
