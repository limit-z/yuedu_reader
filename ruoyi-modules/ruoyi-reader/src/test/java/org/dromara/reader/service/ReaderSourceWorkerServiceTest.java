package org.dromara.reader.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.config.ReaderSourceWorkerProperties;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerItemBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerTaskVo;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceErrorMapper;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.service.cache.ReaderSourceRedisCoordinator;
import org.dromara.reader.service.impl.ReaderSourceWorkerServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderSourceWorkerServiceTest {

    @Mock
    private ReaderSourceWorkerProperties properties;
    @Mock
    private ReaderSourceRedisCoordinator coordinator;
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
    private ReaderSourceChapterSnapshotMapper snapshotMapper;
    @Mock
    private ReaderSourceErrorMapper errorMapper;

    private ReaderSourceWorkerServiceImpl service() {
        return new ReaderSourceWorkerServiceImpl(properties, coordinator, siteMapper, policyMapper, ruleMapper,
            taskMapper, taskRunMapper, snapshotMapper, errorMapper);
    }

    @Test
    void claimShouldReturnFixedRuleAndPolicyContext() {
        ReaderSourceTask task = task("PYTHON");
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourceSite site = site(task.getSiteId());
        ReaderSourceRule rule = rule(task.getRuleId());
        ReaderSourcePolicy policy = policy(task.getPolicyId());
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskRunMapper.selectOne(any())).thenReturn(run);
        when(coordinator.claimRun(run.getId(), "worker-a")).thenReturn(true);
        when(coordinator.isCircuitOpen(site.getId())).thenReturn(false);
        when(siteMapper.selectById(site.getId())).thenReturn(site);
        when(ruleMapper.selectById(rule.getId())).thenReturn(rule);
        when(policyMapper.selectById(policy.getId())).thenReturn(policy);

        ReaderSourceWorkerClaimBo bo = new ReaderSourceWorkerClaimBo();
        bo.setExecutorType("python");
        bo.setWorkerId("worker-a");

        ReaderSourceWorkerTaskVo result = service().claim(bo);

        assertNotNull(result);
        assertEquals(run.getId(), result.getRunId());
        assertEquals(rule.getVersionNo(), result.getRuleVersion());
        assertEquals(policy.getMinDelayMs(), result.getMinDelayMs());
    }

    @Test
    void claimShouldRejectUnknownExecutor() {
        ReaderSourceWorkerClaimBo bo = new ReaderSourceWorkerClaimBo();
        bo.setExecutorType("RUBY");
        bo.setWorkerId("worker-a");

        assertThrows(ServiceException.class, () -> service().claim(bo));
    }

    @Test
    void resultShouldRejectContentHashMismatch() throws Exception {
        ReaderSourceTask task = task("JAVA");
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourceSite site = site(task.getSiteId());
        ReaderSourceRule rule = rule(task.getRuleId());
        when(taskRunMapper.selectById(run.getId())).thenReturn(run);
        when(coordinator.ownsRun(run.getId(), "worker-a")).thenReturn(true);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(siteMapper.selectById(site.getId())).thenReturn(site);
        when(ruleMapper.selectById(rule.getId())).thenReturn(rule);
        when(properties.getMaxItemsPerResult()).thenReturn(100);
        when(properties.getMaxContentBytes()).thenReturn(2 * 1024 * 1024);
        RLock lock = org.mockito.Mockito.mock(RLock.class);
        when(coordinator.resultLock(run.getId())).thenReturn(lock);
        when(lock.tryLock(5, 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        ReaderSourceWorkerItemBo item = new ReaderSourceWorkerItemBo();
        item.setSourceChapterId("ch-1");
        item.setSourceUrl("https://example.com/book/ch-1");
        item.setChapterNo(1);
        item.setChapterName("第一章");
        item.setContent("正文");
        item.setContentHash("0".repeat(64));
        ReaderSourceWorkerResultBo result = new ReaderSourceWorkerResultBo();
        result.setRunId(run.getId());
        result.setRunToken(run.getRunToken());
        result.setWorkerId("worker-a");
        result.setBatchId("batch-1");
        result.setRuleVersion(rule.getVersionNo());
        result.setItems(List.of(item));

        assertThrows(ServiceException.class, () -> service().acceptResult(result));
        verify(snapshotMapper, never()).insert(any(org.dromara.reader.domain.ReaderSourceChapterSnapshot.class));
    }

    private ReaderSourceTask task(String executor) {
        ReaderSourceTask task = new ReaderSourceTask();
        task.setId(12L);
        task.setSiteId(3L);
        task.setRuleId(4L);
        task.setPolicyId(5L);
        task.setExecutorType(executor);
        task.setSourceWorkUrl("https://example.com/book/1");
        task.setStatus("RUNNING");
        task.setStartChapterNo(1);
        return task;
    }

    private ReaderSourceTaskRun run(Long taskId) {
        ReaderSourceTaskRun run = new ReaderSourceTaskRun();
        run.setId(20L);
        run.setTaskId(taskId);
        run.setRunToken("run-token");
        run.setExecutorType("PYTHON");
        run.setStatus("RUNNING");
        return run;
    }

    private ReaderSourceSite site(Long id) {
        ReaderSourceSite site = new ReaderSourceSite();
        site.setId(id);
        site.setAllowedHost("example.com");
        site.setStatus("1");
        site.setComplianceStatus("APPROVED");
        return site;
    }

    private ReaderSourceRule rule(Long id) {
        ReaderSourceRule rule = new ReaderSourceRule();
        rule.setId(id);
        rule.setSiteId(3L);
        rule.setVersionNo(2);
        rule.setStatus("1");
        return rule;
    }

    private ReaderSourcePolicy policy(Long id) {
        ReaderSourcePolicy policy = new ReaderSourcePolicy();
        policy.setId(id);
        policy.setStatus("1");
        policy.setMinDelayMs(3000);
        policy.setRequestsPerMinute(10);
        policy.setDailyRequestLimit(1000);
        policy.setCircuitBreakerThreshold(5);
        return policy;
    }
}
