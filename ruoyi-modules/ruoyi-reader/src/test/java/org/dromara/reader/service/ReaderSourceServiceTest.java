package org.dromara.reader.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.service.cache.ReaderSourceRedisCoordinator;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

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
    @Mock
    private org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper snapshotMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceErrorMapper errorMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceTaskBookMapper taskBookMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceTaskFallbackMapper fallbackMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceTaskLogMapper taskLogMapper;
    @Mock
    private ReaderSourceRedisCoordinator coordinator;

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

        assertEquals("站点尚未完成授权来源确认，不能启用采集许可", exception.getMessage());
    }

    @Test
    void complianceApprovalShouldRequireAuthorizationSource() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(8L);
        when(siteMapper.selectById(8L)).thenReturn(site);

        ReaderSourceComplianceBo bo = new ReaderSourceComplianceBo();
        bo.setApproved(true);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.checkCompliance(8L, bo));

        assertEquals("允许采集时必须填写授权来源说明", exception.getMessage());
        verify(siteMapper, never()).updateById(any(ReaderSourceSite.class));
    }

    @Test
    void complianceApprovalShouldAcceptExternalAuthorizationSource() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(9L);
        when(siteMapper.selectById(9L)).thenReturn(site);

        ReaderSourceComplianceBo bo = new ReaderSourceComplianceBo();
        bo.setApproved(true);
        bo.setAuthorizationNote("版权方合作合同，授权范围包含本系统的公开阅读服务");

        service.checkCompliance(9L, bo);

        assertEquals("APPROVED", site.getComplianceStatus());
        assertEquals(bo.getAuthorizationNote(), site.getAuthorizationNote());
        verify(siteMapper).updateById(site);
    }

    @Test
    void editingSiteMetadataShouldKeepApprovalAndPersistChanges() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(10L);
        site.setSiteName("旧名称");
        site.setBaseUrl("https://example.com");
        site.setAllowedHost("example.com");
        site.setComplianceStatus("APPROVED");
        site.setStatus("1");
        when(siteMapper.selectById(10L)).thenReturn(site);

        ReaderSourceSiteBo bo = new ReaderSourceSiteBo();
        bo.setId(10L);
        bo.setSiteName("新名称");
        bo.setBaseUrl("https://example.com");
        bo.setAllowedHost("example.com");
        bo.setRemark("更新后的备注");

        service.saveSite(bo);

        assertEquals("APPROVED", site.getComplianceStatus());
        assertEquals("1", site.getStatus());
        assertEquals("更新后的备注", site.getRemark());
        verify(siteMapper).updateById(site);
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
    void policyShouldAlwaysHonorRetryAfter() {
        ReaderSourcePolicyBo bo = new ReaderSourcePolicyBo();
        bo.setPolicyName("不可关闭退避策略");
        bo.setHonorRetryAfter("0");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.savePolicy(bo));

        assertEquals("Retry-After 必须遵循，不能关闭", exception.getMessage());
        verify(policyMapper, never()).insert(any(ReaderSourcePolicy.class));
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
        task.setSiteId(3L);
        task.setStatus("DRAFT");
        task.setExecutorType("PYTHON");
        when(taskMapper.selectById(12L)).thenReturn(task);
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(3L);
        site.setStatus("1");
        site.setComplianceStatus("APPROVED");
        when(siteMapper.selectById(3L)).thenReturn(site);
        when(siteMapper.selectList(any())).thenReturn(List.of());
        when(fallbackMapper.selectList(any())).thenReturn(List.of());

        service.startTask(12L);

        ArgumentCaptor<ReaderSourceTaskRun> captor = ArgumentCaptor.forClass(ReaderSourceTaskRun.class);
        verify(taskRunMapper).insert(captor.capture());
        assertEquals(12L, captor.getValue().getTaskId());
        assertEquals("PYTHON", captor.getValue().getExecutorType());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertEquals("RUNNING", task.getStatus());
        verify(taskMapper).updateById(task);
    }

    @Test
    void newTaskCursorShouldStartBeforeRequestedChapter() {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(3L);
        site.setStatus("1");
        site.setComplianceStatus("APPROVED");
        site.setAllowedHost("example.com");
        ReaderSourceRule rule = new ReaderSourceRule();
        rule.setId(4L);
        rule.setSiteId(3L);
        rule.setStatus("1");
        ReaderSourcePolicy policy = new ReaderSourcePolicy();
        policy.setId(5L);
        policy.setStatus("1");
        when(siteMapper.selectById(3L)).thenReturn(site);
        when(ruleMapper.selectById(4L)).thenReturn(rule);
        when(policyMapper.selectById(5L)).thenReturn(policy);
        org.mockito.Mockito.doAnswer(invocation -> {
            ReaderSourceTask inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(taskMapper).insert(any(ReaderSourceTask.class));
        ReaderSourceTask persistedTask = new ReaderSourceTask();
        persistedTask.setId(99L);
        persistedTask.setSiteId(3L);
        when(taskMapper.selectById(99L)).thenReturn(persistedTask);
        when(siteMapper.selectList(any())).thenReturn(List.of());
        when(fallbackMapper.selectList(any())).thenReturn(List.of());

        ReaderSourceTaskBo bo = new ReaderSourceTaskBo();
        bo.setTaskName("从第五章开始");
        bo.setSiteId(3L);
        bo.setRuleId(4L);
        bo.setPolicyId(5L);
        bo.setSourceWorkUrl("https://example.com/book/1");
        bo.setExecutorType("PYTHON");
        bo.setStartChapterNo(5);

        service.saveTask(bo);

        ArgumentCaptor<ReaderSourceTask> captor = ArgumentCaptor.forClass(ReaderSourceTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getCurrentChapterNo());
    }

    @Test
    void dailyLimitRetryShouldOnlyResumeTaskFromAnEarlierDay() {
        ReaderSourceTask task = new ReaderSourceTask();
        task.setId(20L);
        task.setStatus("PAUSED");
        task.setExecutorType("PYTHON");
        task.setDailyRetryEnabled("1");
        task.setFailReason("站点每日请求额度已耗尽");
        task.setUpdateTime(LocalDateTime.now().minusDays(1));
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        assertEquals(1, service.retryDailyLimitTasks());

        ArgumentCaptor<ReaderSourceTaskRun> captor = ArgumentCaptor.forClass(ReaderSourceTaskRun.class);
        verify(taskRunMapper).insert(captor.capture());
        assertEquals("DAILY_LIMIT", captor.getValue().getTriggerType());
        assertEquals(1, captor.getValue().getRetryNo());
        assertEquals(1, task.getDailyRetryCount());
        assertEquals("RUNNING", task.getStatus());
        assertEquals(null, task.getFailReason());
    }

    @Test
    void dailyLimitRetryShouldIgnoreTaskPausedToday() {
        ReaderSourceTask task = new ReaderSourceTask();
        task.setId(21L);
        task.setStatus("PAUSED");
        task.setDailyRetryEnabled("1");
        task.setFailReason("站点每日请求额度已耗尽");
        task.setUpdateTime(LocalDateTime.now());
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        assertEquals(0, service.retryDailyLimitTasks());
        verify(taskRunMapper, never()).insert(any(ReaderSourceTaskRun.class));
    }

    @Test
    void circuitOpenRetryShouldResetCircuitAndCreateRetryRun() {
        ReaderSourceTask task = new ReaderSourceTask();
        task.setId(30L);
        task.setSiteId(3L);
        task.setStatus("FAILED");
        task.setExecutorType("PYTHON");
        task.setFailureCode("CIRCUIT_OPEN");
        task.setFailReason("连续失败达到熔断阈值");
        task.setAutoRetryCount(2);

        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(3L);
        site.setStatus("1");
        site.setComplianceStatus("APPROVED");

        when(taskMapper.selectById(30L)).thenReturn(task);
        when(siteMapper.selectById(3L)).thenReturn(site);
        when(taskRunMapper.selectOne(any())).thenReturn(null);
        when(fallbackMapper.selectList(any())).thenReturn(List.of());
        when(siteMapper.selectList(any())).thenReturn(List.of());

        service.retryCircuitOpenTask(30L);

        verify(coordinator).resetCircuit(3L);
        ArgumentCaptor<ReaderSourceTaskRun> captor = ArgumentCaptor.forClass(ReaderSourceTaskRun.class);
        verify(taskRunMapper).insert(captor.capture());
        assertEquals("CIRCUIT_RETRY", captor.getValue().getTriggerType());
        assertEquals(3, captor.getValue().getRetryNo());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertEquals("RUNNING", task.getStatus());
        assertEquals(null, task.getFailureCode());
        assertEquals(null, task.getFailReason());
        verify(taskMapper).update(isNull(), any());
    }
}
