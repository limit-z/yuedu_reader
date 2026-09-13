package org.dromara.reader.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.config.ReaderSourceWorkerProperties;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderWorkCategoryMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
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
import org.dromara.reader.service.impl.ReaderCoverService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderWork.class);
        initializeTableInfo(ReaderNovelChapter.class);
    }

    @Mock
    private ReaderSourceWorkerProperties properties;
    @Mock
    private org.dromara.reader.service.impl.ReaderCoverCrawlerService readerCoverCrawlerService;
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
    @Mock
    private ReaderSourceTaskBookMapper taskBookMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceTaskFallbackMapper fallbackMapper;
    @Mock
    private org.dromara.reader.mapper.ReaderSourceTaskLogMapper taskLogMapper;
    @Mock
    private IReaderSourceService sourceService;
    @Mock
    private ReaderWorkMapper workMapper;
    @Mock
    private ReaderWorkCategoryMapper categoryMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderNovelChapterContentMapper novelChapterContentMapper;
    @Mock
    private ReaderContentAuditMapper contentAuditMapper;
    @Mock
    private ReaderCoverService readerCoverService;

    private ReaderSourceWorkerServiceImpl service() {
        return new ReaderSourceWorkerServiceImpl(properties, coordinator, siteMapper, policyMapper, ruleMapper,
            taskMapper, taskRunMapper, snapshotMapper, errorMapper, taskBookMapper, fallbackMapper, taskLogMapper,
            sourceService, workMapper, categoryMapper,
            novelChapterMapper, novelChapterContentMapper, contentAuditMapper, readerCoverService, readerCoverCrawlerService);
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
        when(coordinator.claimSite(site.getId(), run.getId(), "worker-a", policy.getConcurrencyLimit())).thenReturn(true);
        when(coordinator.isCircuitOpen(site.getId())).thenReturn(false);
        when(siteMapper.selectById(site.getId())).thenReturn(site);
        when(ruleMapper.selectById(rule.getId())).thenReturn(rule);
        when(policyMapper.selectById(task.getPolicyId())).thenReturn(policy(task.getPolicyId()));
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
    void heartbeatShouldRenewRunAndSiteLeases() {
        ReaderSourceTask task = task("PYTHON");
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourceSite site = site(task.getSiteId());
        ReaderSourcePolicy policy = policy(task.getPolicyId());
        when(taskRunMapper.selectById(run.getId())).thenReturn(run);
        when(coordinator.ownsRun(run.getId(), "worker-a")).thenReturn(true);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(policyMapper.selectById(task.getPolicyId())).thenReturn(policy);
        when(coordinator.renewRun(run.getId(), "worker-a")).thenReturn(true);
        when(coordinator.renewSite(site.getId(), run.getId(), "worker-a", policy.getConcurrencyLimit())).thenReturn(true);

        ReaderSourceWorkerHeartbeatBo bo = new ReaderSourceWorkerHeartbeatBo();
        bo.setRunId(run.getId());
        bo.setRunToken(run.getRunToken());
        bo.setWorkerId("worker-a");

        service().heartbeat(bo);

        verify(coordinator).renewRun(run.getId(), "worker-a");
        verify(coordinator).renewSite(site.getId(), run.getId(), "worker-a", policy.getConcurrencyLimit());
        verify(taskRunMapper).updateById(run);
    }

    @Test
    void heartbeatShouldRecoverWhenSiteLeaseIsLost() {
        ReaderSourceTask task = task("PYTHON");
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourcePolicy policy = policy(task.getPolicyId());
        when(taskRunMapper.selectById(run.getId())).thenReturn(run);
        when(coordinator.ownsRun(run.getId(), "worker-a")).thenReturn(true);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(policyMapper.selectById(task.getPolicyId())).thenReturn(policy);
        when(coordinator.renewRun(run.getId(), "worker-a")).thenReturn(true);
        when(coordinator.renewSite(task.getSiteId(), run.getId(), "worker-a", policy.getConcurrencyLimit())).thenReturn(false);
        when(taskBookMapper.selectList(any())).thenReturn(List.of());
        when(taskRunMapper.selectOne(any())).thenReturn(null);

        ReaderSourceWorkerHeartbeatBo bo = new ReaderSourceWorkerHeartbeatBo();
        bo.setRunId(run.getId());
        bo.setRunToken(run.getRunToken());
        bo.setWorkerId("worker-a");

        assertThrows(ServiceException.class, () -> service().heartbeat(bo));

        verify(taskRunMapper).updateById(run);
        verify(taskRunMapper).insert(any(ReaderSourceTaskRun.class));
        verify(coordinator).releaseRun(run.getId(), "worker-a");
        verify(coordinator).releaseSite(task.getSiteId(), run.getId(), "worker-a", policy.getConcurrencyLimit());
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

    @Test
    void completedSingleCollectionShouldCreateDraftWork() throws Exception {
        ReaderSourceTask task = task("JAVA");
        task.setSourceWorkTitle("测试作品");
        task.setCollectionMode("SINGLE");
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourceSite site = site(task.getSiteId());
        ReaderSourceRule rule = rule(task.getRuleId());
        when(taskRunMapper.selectById(run.getId())).thenReturn(run);
        when(coordinator.ownsRun(run.getId(), "worker-a")).thenReturn(true);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(siteMapper.selectById(site.getId())).thenReturn(site);
        when(ruleMapper.selectById(rule.getId())).thenReturn(rule);
        when(policyMapper.selectById(task.getPolicyId())).thenReturn(policy(task.getPolicyId()));
        when(properties.getMaxItemsPerResult()).thenReturn(100);
        when(properties.getMaxContentBytes()).thenReturn(2 * 1024 * 1024);
        RLock lock = org.mockito.Mockito.mock(RLock.class);
        when(coordinator.resultLock(run.getId())).thenReturn(lock);
        when(lock.tryLock(5, 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        ReaderSourceWorkerItemBo item = new ReaderSourceWorkerItemBo();
        item.setSourceChapterId("1");
        item.setSourceUrl("https://example.com/book/1.html");
        item.setChapterNo(1);
        item.setChapterName("第一章");
        item.setContent("第一段。    第二段。");
        item.setContentHash(sha256(org.dromara.reader.service.impl.ReaderNovelTextFormatter.format(item.getContent())));
        item.setAuthorName("测试作者");
        item.setCategoryName("玄幻魔法");
        item.setSerialStatus("已完结");
        ReaderSourceWorkerResultBo result = new ReaderSourceWorkerResultBo();
        result.setRunId(run.getId());
        result.setRunToken(run.getRunToken());
        result.setWorkerId("worker-a");
        result.setExecutorType("JAVA");
        result.setBatchId("batch-completed");
        result.setRuleVersion(rule.getVersionNo());
        result.setItems(List.of(item));
        result.setCompleted(true);

        service().acceptResult(result);

        org.mockito.ArgumentCaptor<ReaderWork> workCaptor = org.mockito.ArgumentCaptor.forClass(ReaderWork.class);
        verify(workMapper).insert(workCaptor.capture());
        verify(readerCoverService).ensureGeneratedCover(workCaptor.getValue());
        assertEquals("测试作者", workCaptor.getValue().getAuthorName());
        assertEquals("玄幻魔法", workCaptor.getValue().getCategoryName());
        assertEquals("FINISHED", workCaptor.getValue().getSerialStatus());
        org.mockito.ArgumentCaptor<org.dromara.reader.domain.ReaderSourceChapterSnapshot> snapshotCaptor =
            org.mockito.ArgumentCaptor.forClass(org.dromara.reader.domain.ReaderSourceChapterSnapshot.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        assertEquals("第一段。\n\n第二段。", snapshotCaptor.getValue().getContent());
    }

    @Test
    void openEndedBatchBookShouldNotUseDiscoveryLatestChapterAsEnd() {
        ReaderSourceTask task = task("PYTHON");
        task.setCollectionMode("CATEGORY");
        task.setEndChapterNo(null);
        ReaderSourceTaskRun run = run(task.getId());
        ReaderSourceSite site = site(task.getSiteId());
        ReaderSourceRule rule = rule(task.getRuleId());
        ReaderSourcePolicy policy = policy(task.getPolicyId());
        ReaderSourceTaskBook book = new ReaderSourceTaskBook();
        book.setId(30L);
        book.setTaskId(task.getId());
        book.setSourceWorkUrl("https://example.com/book/1");
        book.setSourceWorkTitle("测试作品");
        book.setLocalLatestChapterNo(0);
        book.setRemoteLatestChapterNo(2);
        book.setStatus("QUEUED");

        when(taskRunMapper.selectById(run.getId())).thenReturn(run);
        when(coordinator.ownsRun(run.getId(), "worker-a")).thenReturn(true);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(siteMapper.selectById(site.getId())).thenReturn(site);
        when(ruleMapper.selectById(rule.getId())).thenReturn(rule);
        when(policyMapper.selectById(policy.getId())).thenReturn(policy);
        when(taskBookMapper.selectOne(any())).thenReturn(book);

        ReaderSourceWorkerTaskVo result = service().claimNextBook(run.getId(), run.getRunToken(), "worker-a");

        assertNotNull(result);
        assertEquals(1, result.getStartChapterNo());
        assertEquals(null, result.getEndChapterNo());
        verify(taskBookMapper).updateById(book);
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

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static void initializeTableInfo(Class<?> entityClass) {
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityClass) != null) return;
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), entityClass.getName());
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entityClass);
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
        policy.setConcurrencyLimit(1);
        policy.setMinDelayMs(3000);
        policy.setRequestsPerMinute(10);
        policy.setDailyRequestLimit(1000);
        policy.setCircuitBreakerThreshold(5);
        return policy;
    }
}
