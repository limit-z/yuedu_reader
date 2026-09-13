package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.reader.config.ReaderSourceWorkerProperties;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderSourceTaskFallback;
import org.dromara.reader.domain.ReaderSourceTaskLog;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.ReaderWorkCategory;
import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerErrorBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerItemBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerMetricsBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerPermitBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerBooksBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerBookBo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerAckVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerPermitVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerTaskVo;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceErrorMapper;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderSourceTaskFallbackMapper;
import org.dromara.reader.mapper.ReaderSourceTaskLogMapper;
import org.dromara.reader.mapper.ReaderWorkCategoryMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.service.IReaderSourceWorkerService;
import org.dromara.reader.service.cache.ReaderSourceRedisCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 统一 Worker 协议实现，负责校验、幂等、快照和任务状态推进。 */
@RequiredArgsConstructor
@Service
public class ReaderSourceWorkerServiceImpl implements IReaderSourceWorkerService {

    private static final String RUNNING = "RUNNING";
    private static final String PAUSED = "PAUSED";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String WAITING_REVIEW = "WAITING_REVIEW";
    /** 已启用标记，统一用于站点、策略、任务自动化开关判断。 */
    private static final String ENABLED = "1";
    private static final String APPROVED = "APPROVED";
    private static final Set<String> EXECUTORS = Set.of("JAVA", "PYTHON", "GO");
    private static final Set<String> ERROR_TYPES = Set.of("HTTP", "TIMEOUT", "PARSE", "POLICY", "SSRF", "QUALITY", "RATE_LIMIT", "UNKNOWN");

    private final ReaderSourceWorkerProperties properties;
    private final ReaderSourceRedisCoordinator coordinator;
    private final ReaderSourceSiteMapper siteMapper;
    private final ReaderSourcePolicyMapper policyMapper;
    private final ReaderSourceRuleMapper ruleMapper;
    private final ReaderSourceTaskMapper taskMapper;
    private final ReaderSourceTaskRunMapper taskRunMapper;
    private final ReaderSourceChapterSnapshotMapper snapshotMapper;
    private final ReaderSourceErrorMapper errorMapper;
    private final ReaderSourceTaskBookMapper taskBookMapper;
    /** 备用书源路由，用于授权失败时创建可追溯的续采任务。 */
    private final ReaderSourceTaskFallbackMapper fallbackMapper;
    /** Worker 协议和采集结果的任务事件日志。 */
    private final ReaderSourceTaskLogMapper taskLogMapper;
    /** 自动从其他已授权站点编排备用路由。 */
    private final org.dromara.reader.service.IReaderSourceService sourceService;
    private final ReaderWorkMapper workMapper;
    private final ReaderWorkCategoryMapper categoryMapper;
    private final ReaderNovelChapterMapper novelChapterMapper;
    private final ReaderNovelChapterContentMapper novelChapterContentMapper;
    private final ReaderContentAuditMapper contentAuditMapper;
    private final ReaderCoverService readerCoverService;
    private final ReaderCoverCrawlerService readerCoverCrawlerService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderSourceWorkerTaskVo claim(ReaderSourceWorkerClaimBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getWorkerId())) {
            throw new ServiceException("Worker 标识不能为空");
        }
        String workerId = trimWorkerId(bo.getWorkerId());
        String executorType = normalizeExecutor(bo.getExecutorType());
        List<ReaderSourceTask> tasks = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
            .eq(ReaderSourceTask::getStatus, RUNNING)
            .eq(ReaderSourceTask::getExecutorType, executorType)
            .orderByAsc(ReaderSourceTask::getUpdateTime).orderByAsc(ReaderSourceTask::getId));
        for (ReaderSourceTask task : tasks) {
            ReaderSourceTaskRun run = latestRunningRun(task.getId());
            if (run == null) {
                continue;
            }
            ReaderSourceSite site = siteMapper.selectById(task.getSiteId());
            ReaderSourceRule rule = ruleMapper.selectById(task.getRuleId());
            ReaderSourcePolicy policy = policyMapper.selectById(task.getPolicyId());
            if (site == null || rule == null || policy == null || !"1".equals(site.getStatus())
                || !"APPROVED".equals(site.getComplianceStatus()) || !"1".equals(policy.getStatus())) {
                continue;
            }
            if (coordinator.isCircuitOpen(site.getId())) {
                continue;
            }
            if (!coordinator.claimRun(run.getId(), workerId)) continue;
            if (!coordinator.claimSite(site.getId(), run.getId(), workerId, policy.getConcurrencyLimit())) {
                coordinator.releaseRun(run.getId(), workerId);
                continue;
            }
            if (run.getClaimedAt() == null) {
                run.setClaimedAt(LocalDateTime.now());
                taskRunMapper.updateById(run);
            }
            // A claimed run is a new execution attempt. Keep historical errors in
            // the error and log tables, but do not show them as the current state.
            if (task.getFailureCode() != null || task.getFailReason() != null) {
                clearFailureState(task);
                taskMapper.updateById(task);
            }
            writeLog(task.getId(), run.getId(), null, "INFO", "CLAIM", "Worker 已自动领取运行记录", "{\"workerId\":\"" + workerId + "\"}");
            return toTaskVo(task, run, rule, policy, workerId);
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(Math.max(properties.getClaimLeaseSeconds() * 2L,
            properties.getStaleRunSeconds()));
        List<ReaderSourceTaskRun> staleRuns = taskRunMapper.selectList(Wrappers.<ReaderSourceTaskRun>lambdaQuery()
            .eq(ReaderSourceTaskRun::getStatus, RUNNING)
            .isNotNull(ReaderSourceTaskRun::getClaimedAt)
            .lt(ReaderSourceTaskRun::getHeartbeatAt, staleBefore)
            .orderByAsc(ReaderSourceTaskRun::getHeartbeatAt));
        int recovered = 0;
        for (ReaderSourceTaskRun stale : staleRuns) {
            ReaderSourceTaskRun current = taskRunMapper.selectById(stale.getId());
            if (current == null || !RUNNING.equals(current.getStatus()) || current.getHeartbeatAt() == null
                || !current.getHeartbeatAt().isBefore(staleBefore)) {
                continue;
            }
            // A newly created replacement run waits in the database until a worker
            // claims it. It has no Redis lease yet and must not be mistaken for a
            // dead worker on every scheduler tick.
            if (current.getClaimedAt() == null && !coordinator.hasRunLease(current.getId())) {
                current.setHeartbeatAt(now);
                taskRunMapper.updateById(current);
                continue;
            }
            ReaderSourceTask task = taskMapper.selectById(current.getTaskId());
            if (task == null) continue;
            ReaderSourcePolicy policy = policyMapper.selectById(task.getPolicyId());
            String reason = "Worker 心跳超过 " + Math.max(properties.getClaimLeaseSeconds() * 2L,
                properties.getStaleRunSeconds()) + " 秒未更新，运行租约已自动回收";
            current.setStatus(FAILED);
            current.setFinishedAt(now);
            current.setErrorMessage(reason);
            current.setResultSummary("{\"recovery\":\"LEASE_TIMEOUT\"}");
            taskRunMapper.updateById(current);
            requeueRunningBooks(current.getId(), reason);
            coordinator.forceReleaseRun(current.getId());
            if (policy != null) {
                coordinator.forceReleaseSite(task.getSiteId(), current.getId(), policy.getConcurrencyLimit());
            }
            if (RUNNING.equals(task.getStatus()) && latestRunningRun(task.getId()) == null) {
                ReaderSourceTaskRun replacement = new ReaderSourceTaskRun();
                replacement.setTaskId(task.getId());
                replacement.setRunToken(UUID.randomUUID().toString());
                replacement.setExecutorType(task.getExecutorType());
                replacement.setStatus(RUNNING);
                replacement.setStartedAt(now);
                replacement.setHeartbeatAt(now);
                replacement.setRequestCount(0);
                replacement.setSuccessCount(0);
                replacement.setSkippedCount(0);
                replacement.setFailureCount(0);
                replacement.setTooManyRequestsCount(0);
                replacement.setCircuitOpen("0");
                replacement.setRetryNo(safeAdd(current.getRetryNo(), 1));
                replacement.setTriggerType("LEASE_RECOVERY");
                replacement.setTriggerReason(reason);
                taskRunMapper.insert(replacement);
                task.setLastRunAt(now);
                task.setFailReason(reason + "，已创建续采运行");
                taskMapper.updateById(task);
            }
            recovered++;
        }
        return recovered + normalizeOrphanedRunningTasks(now);
    }

    /**
     * 将历史错误回执或租约恢复留下的 RUNNING 任务归一化，避免页面继续显示已结束的旧 403。
     * 授权、额度和安全阻断不会被这里自动重试，只会恢复为明确的 PAUSED 状态。
     */
    private int normalizeOrphanedRunningTasks(LocalDateTime now) {
        List<ReaderSourceTask> tasks = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
            .eq(ReaderSourceTask::getStatus, RUNNING)
            .orderByAsc(ReaderSourceTask::getUpdateTime));
        int normalized = 0;
        for (ReaderSourceTask task : tasks) {
            ReaderSourceTaskRun running = latestRunningRun(task.getId());
            if (running != null) {
                // 熔断是站点级阻断，尚未领取的排队运行不可能被 Worker 执行。
                // 明确结束这条排队运行，避免任务永久显示为 RUNNING。
                if (coordinator.isCircuitOpen(task.getSiteId()) && running.getClaimedAt() == null) {
                    String reason = "站点当前处于熔断状态，运行记录未领取，已暂停等待人工重置熔断";
                    running.setStatus(PAUSED);
                    running.setFinishedAt(now);
                    running.setCircuitOpen("1");
                    running.setErrorMessage(reason);
                    running.setResultSummary("{\"recovery\":\"CIRCUIT_OPEN_WAITING\"}");
                    taskRunMapper.updateById(running);
                    requeueRunningBooks(running.getId(), reason);
                    task.setStatus(PAUSED);
                    task.setFailureCode("CIRCUIT_OPEN");
                    task.setFailReason(reason);
                    taskMapper.updateById(task);
                    writeLog(task.getId(), running.getId(), null, "WARN", "RECOVERY", reason,
                        "{\"failureCode\":\"CIRCUIT_OPEN\",\"claimed\":false}");
                    normalized++;
                }
                continue;
            }
            ReaderSourceTaskRun latest = taskRunMapper.selectOne(Wrappers.<ReaderSourceTaskRun>lambdaQuery()
                .eq(ReaderSourceTaskRun::getTaskId, task.getId())
                .orderByDesc(ReaderSourceTaskRun::getCreateTime)
                .orderByDesc(ReaderSourceTaskRun::getId)
                .last("LIMIT 1"));
            if (latest == null || RUNNING.equals(latest.getStatus())) continue;
            if (!List.of("HTTP_401", "HTTP_403", "DAILY_LIMIT", "SSRF", "POLICY", "CIRCUIT_OPEN")
                .contains(task.getFailureCode())) {
                continue;
            }
            String reason = StringUtils.isBlank(task.getFailReason())
                ? "最近运行记录已结束，保留原失败原因等待处理"
                : task.getFailReason();
            requeueRunningBooks(latest.getId(), reason);
            task.setStatus(PAUSED);
            task.setFailReason(reason);
            taskMapper.updateById(task);
            writeLog(task.getId(), latest.getId(), null, "WARN", "RECOVERY",
                "已归一化孤儿运行状态，保留授权阻断原因等待处理",
                "{\"previousRunStatus\":\"" + latest.getStatus() + "\",\"failureCode\":\""
                    + task.getFailureCode() + "\",\"normalizedAt\":\"" + now + "\"}");
            normalized++;
        }
        return normalized;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int synchronizeTaskProgress() {
        int changed = 0;
        List<ReaderSourceTask> tasks = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
            .in(ReaderSourceTask::getStatus, List.of(RUNNING, PAUSED, FAILED, WAITING_REVIEW, COMPLETED)));
        for (ReaderSourceTask task : tasks) {
            List<ReaderSourceTaskBook> books = taskBookMapper.selectList(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
                .eq(ReaderSourceTaskBook::getTaskId, task.getId()));
            if (books.isEmpty()) continue;
            int total = books.size();
            int processed = 0;
            int success = 0;
            int skipped = 0;
            int failed = 0;
            for (ReaderSourceTaskBook book : books) {
                String status = book.getStatus();
                boolean terminal = "WAITING_REVIEW".equals(status) || "COMPLETED".equals(status)
                    || "SKIPPED".equals(status) || "FAILED".equals(status);
                if (terminal) processed++;
                if ("WAITING_REVIEW".equals(status)
                    || ("COMPLETED".equals(status) && positive(book.getSuccessChapterCount()))) success++;
                if ("SKIPPED".equals(status)
                    || (terminal && !positive(book.getSuccessChapterCount()) && positive(book.getSkippedChapterCount()))) skipped++;
                if ("FAILED".equals(status) || positive(book.getFailureChapterCount())
                    || StringUtils.isNotBlank(book.getLastError())) failed++;
            }
            int progress = total == 0 ? 0 : Math.min(100, processed * 100 / total);
            boolean different = !Integer.valueOf(total).equals(task.getTotalBooks())
                || !Integer.valueOf(processed).equals(task.getProcessedBooks())
                || !Integer.valueOf(success).equals(task.getSuccessBooks())
                || !Integer.valueOf(skipped).equals(task.getSkippedBooks())
                || !Integer.valueOf(failed).equals(task.getFailedBooks())
                || !Integer.valueOf(progress).equals(task.getProgressPercent());
            if (different) {
                task.setTotalBooks(total);
                task.setProcessedBooks(processed);
                task.setSuccessBooks(success);
                task.setSkippedBooks(skipped);
                task.setFailedBooks(failed);
                task.setProgressPercent(progress);
                taskMapper.updateById(task);
                changed++;
            }
        }
        return changed;
    }

    @Override
    public void heartbeat(ReaderSourceWorkerHeartbeatBo bo) {
        ReaderSourceTaskRun run = requireRunIdentity(bo == null ? null : bo.getRunId(),
            bo == null ? null : bo.getRunToken(), bo == null ? null : bo.getWorkerId());
        ReaderSourceTask task = requireTask(run.getTaskId());
        ReaderSourcePolicy policy = requirePolicy(task.getPolicyId());
        if (!coordinator.renewRun(run.getId(), bo.getWorkerId())) {
            handleLeaseLoss(run, task, policy, bo.getWorkerId(), "Worker 运行租约已失效，已自动回收并创建续采运行");
            throw new ServiceException("Worker 租约已失效，请重新领取任务");
        }
        if (!coordinator.renewSite(task.getSiteId(), run.getId(), bo.getWorkerId(), policy.getConcurrencyLimit())) {
            handleLeaseLoss(run, task, policy, bo.getWorkerId(), "站点并发租约已失效，已自动回收并创建续采运行");
            throw new ServiceException("站点并发租约已失效，请重新领取任务");
        }
        run.setHeartbeatAt(LocalDateTime.now());
        taskRunMapper.updateById(run);
    }

    /**
     * 心跳确认租约丢失时立即收敛数据库状态，避免旧运行永久 RUNNING 并阻塞任务续采。
     * 新运行仍通过正常 claim 流程取得租约，旧 Worker 的迟到回执会被令牌和状态校验拒绝。
     */
    private void handleLeaseLoss(ReaderSourceTaskRun run, ReaderSourceTask task, ReaderSourcePolicy policy,
                                 String workerId, String reason) {
        if (!RUNNING.equals(run.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        run.setStatus(FAILED);
        run.setFinishedAt(now);
        run.setErrorMessage(reason);
        run.setResultSummary("{\"recovery\":\"LEASE_LOST\"}");
        taskRunMapper.updateById(run);
        List<ReaderSourceTaskBook> books = requeueRunningBooks(run.getId(), reason);
        task.setStatus(RUNNING);
        task.setFailureCode(null);
        task.setFailReason(reason);
        taskMapper.updateById(task);
        coordinator.releaseRun(run.getId(), workerId);
        coordinator.releaseSite(task.getSiteId(), run.getId(), workerId, policy.getConcurrencyLimit());
        if (latestRunningRun(task.getId()) == null) {
            createReplacementRun(task, run, "LEASE_RECOVERY", reason, now);
        }
        writeLog(task.getId(), run.getId(), books.isEmpty() ? null : books.getFirst().getId(),
            "WARN", "RECOVERY", reason, "{\"recovery\":\"LEASE_LOST\"}");
    }

    @Override
    public ReaderSourceWorkerPermitVo acquirePermit(ReaderSourceWorkerPermitBo bo) {
        ReaderSourceTaskRun run = requireOwnedRun(bo == null ? null : bo.getRunId(), bo == null ? null : bo.getRunToken(),
            bo == null ? null : bo.getWorkerId());
        ReaderSourceTask task = requireTask(run.getTaskId());
        ReaderSourcePolicy policy = requirePolicy(task.getPolicyId());
        ReaderSourceRedisCoordinator.Permit permit = coordinator.acquireSitePermit(task.getSiteId(),
            policy.getRequestsPerMinute(), policy.getDailyRequestLimit(), policy.getMinDelayMs());
        ReaderSourceWorkerPermitVo vo = new ReaderSourceWorkerPermitVo();
        vo.setAllowed(permit.allowed());
        vo.setRetryAfterMs(permit.retryAfterMs());
        vo.setReason(permit.reason());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderSourceWorkerAckVo acceptResult(ReaderSourceWorkerResultBo bo) {
        validateResultRequest(bo);
        ReaderSourceTaskRun run = requireOwnedRun(bo.getRunId(), bo.getRunToken(), bo.getWorkerId());
        ReaderSourceTask task = requireTask(run.getTaskId());
        ReaderSourceSite site = requireSite(task.getSiteId());
        ReaderSourceRule rule = requireRule(task.getRuleId());
        if (!task.getExecutorType().equals(normalizeExecutor(bo.getExecutorType()))) {
            throw new ServiceException("Worker 执行器与任务配置不一致");
        }
        if (!rule.getVersionNo().equals(bo.getRuleVersion())) {
            throw new ServiceException("Worker 使用的规则版本与任务不一致");
        }
        var lock = coordinator.resultLock(run.getId());
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("结果正在处理中，请稍后重试");
            }
            if (coordinator.isBatchProcessed(run.getId(), bo.getBatchId())) {
                return toAck(run, task, 0, 0);
            }
            int accepted = 0;
            int skipped = 0;
            Map<Long, Long> coverTriggers = new HashMap<>();
            for (ReaderSourceWorkerItemBo item : bo.getItems()) {
                ReaderSourceTaskBook taskBook = null;
                if (!isSingleTask(task) && item.getTaskBookId() != null) {
                    taskBook = taskBookMapper.selectById(item.getTaskBookId());
                }
                validateItem(item, task, taskBook, site);
                if (!isSingleTask(task) && item.getTaskBookId() == null) {
                    throw new ServiceException("批量采集章节必须绑定任务书籍明细");
                }
                if (item.getTaskBookId() != null) {
                    if (taskBook == null || !task.getId().equals(taskBook.getTaskId())) {
                        throw new ServiceException("章节所属任务书籍明细不存在");
                    }
                    applyTaskBookMetadata(taskBook, item);
                }
                String normalizedContent = ReaderNovelTextFormatter.format(item.getContent());
                String normalizedContentHash = sha256(normalizedContent);
                ReaderSourceChapterSnapshot same = snapshotMapper.selectByContentHash(task.getId(), item.getSourceChapterId(),
                    normalizedContentHash);
                if (same != null) {
                    skipped++;
                    continue;
                }
                ReaderSourceChapterSnapshot previous = snapshotMapper.selectLatestByChapter(task.getId(), item.getSourceChapterId());
                ReaderSourceChapterSnapshot snapshot = new ReaderSourceChapterSnapshot();
                ReaderWork work = ensureCollectionWork(task, taskBook, item);
                coverTriggers.put(work.getId(), item.getTaskBookId());
                snapshot.setTaskId(task.getId());
                snapshot.setTaskBookId(item.getTaskBookId());
                snapshot.setWorkId(work.getId());
                snapshot.setRunId(run.getId());
                snapshot.setSourceChapterId(item.getSourceChapterId().trim());
                snapshot.setSourceUrl(sanitizeUrl(item.getSourceUrl(), site.getAllowedHost()));
                snapshot.setChapterNo(item.getChapterNo());
                snapshot.setChapterName(trimToLength(item.getChapterName(), 255));
                snapshot.setContentHash(normalizedContentHash);
                snapshot.setTitleHash(normalizeOptionalHash(item.getTitleHash(), item.getChapterName()));
                snapshot.setContent(normalizedContent);
                snapshot.setSnapshotStatus(previous == null ? "NEW" : "CHANGED");
                snapshot.setCapturedAt(LocalDateTime.now());
                snapshotMapper.insert(snapshot);
                upsertCollectedChapter(work.getId(), snapshot);
                accepted++;
            }
            ReaderSourceWorkerMetricsBo metrics = bo.getMetrics();
            int requests = metric(metrics == null ? null : metrics.getRequests(), bo.getItems().size());
            int remoteSkipped = metric(metrics == null ? null : metrics.getSkipped(), 0);
            int failures = metric(metrics == null ? null : metrics.getFailures(), 0);
            int tooManyRequests = metric(metrics == null ? null : metrics.getTooManyRequests(), 0);
            run.setRequestCount(safeAdd(run.getRequestCount(), requests));
            run.setSuccessCount(safeAdd(run.getSuccessCount(), accepted));
            run.setSkippedCount(safeAdd(run.getSkippedCount(), skipped + remoteSkipped));
            run.setFailureCount(safeAdd(run.getFailureCount(), failures));
            run.setTooManyRequestsCount(safeAdd(run.getTooManyRequestsCount(), tooManyRequests));
            run.setHeartbeatAt(LocalDateTime.now());
            run.setResultSummary("{\"accepted\":" + accepted + ",\"skipped\":" + skipped + "}");
            task.setCurrentChapterNo(max(task.getCurrentChapterNo(), bo.getCursorChapterNo()));
            updateTaskBookProgress(task, bo, accepted, skipped);
            updateFallbackSourceBook(task, accepted, skipped, Boolean.TRUE.equals(bo.getCompleted()));
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                if (isSingleTask(task)) {
                    run.setStatus(COMPLETED);
                    run.setFinishedAt(LocalDateTime.now());
                    task.setStatus(accepted > 0 ? WAITING_REVIEW : COMPLETED);
                } else if (countQueuedBooks(task.getId()) == 0) {
                    run.setStatus(COMPLETED);
                    run.setFinishedAt(LocalDateTime.now());
                    task.setStatus(hasWaitingReviewBooks(task.getId()) ? WAITING_REVIEW : COMPLETED);
                } else {
                    // A chapter batch is not the end of a batch task. Remaining books
                    // must keep the task running until every task-book reaches a terminal state.
                    task.setStatus(RUNNING);
                }
            }
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                clearFailureState(task);
                // 只有本次章节批次明确结束时才创建封面任务，避免只采集到首章就触发外部图片采集。
                coverTriggers.forEach((workId, taskBookId) ->
                    readerCoverCrawlerService.ensureTask(workId, task.getId(), taskBookId));
            }
            taskRunMapper.updateById(run);
            taskMapper.updateById(task);
            writeLog(task.getId(), run.getId(), bo.getItems().isEmpty() ? null : bo.getItems().getFirst().getTaskBookId(),
                "INFO", "RESULT", "已接收章节结果批次", "{\"accepted\":" + accepted + ",\"skipped\":" + skipped + ",\"completed\":" + Boolean.TRUE.equals(bo.getCompleted()) + "}");
            coordinator.markBatchProcessed(run.getId(), bo.getBatchId());
            coordinator.recordSuccess(site.getId());
            if (!RUNNING.equals(run.getStatus())) {
                releaseLeases(task, run, bo.getWorkerId());
            }
            return toAck(run, task, accepted, skipped);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("结果处理被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 接收批量任务的来源书籍发现结果，服务端统一执行去重、增量范围计算和分类登记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderSourceWorkerAckVo acceptBooks(ReaderSourceWorkerBooksBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getBatchId()) || bo.getBooks() == null || bo.getBooks().isEmpty()
            || bo.getBooks().size() > properties.getMaxItemsPerResult()) {
            throw new ServiceException("书籍发现批次和书籍列表不能为空，且批次不能超过限制");
        }
        if (!bo.getBatchId().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ServiceException("书籍发现批次标识格式不正确");
        }
        ReaderSourceTaskRun run = requireOwnedRun(bo.getRunId(), bo.getRunToken(), bo.getWorkerId());
        ReaderSourceTask task = requireTask(run.getTaskId());
        if (isSingleTask(task)) {
            throw new ServiceException("单本采集任务不接受批量书籍发现结果");
        }
        ReaderSourceSite site = requireSite(task.getSiteId());
        var lock = coordinator.resultLock(run.getId());
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) throw new ServiceException("书籍发现结果正在处理中，请稍后重试");
            if (coordinator.isBatchProcessed(run.getId(), "books:" + bo.getBatchId())) {
                return toAck(run, task, 0, 0);
            }
            if (task.getBatchNo() == null) task.setBatchNo(UUID.randomUUID().toString());
            int accepted = 0;
            int skipped = 0;
            int unchanged = 0;
            Set<String> batchDedupeKeys = new HashSet<>();
            int discovered = 0;
            for (ReaderSourceWorkerBookBo item : bo.getBooks()) {
                if ((task.getTotalBooks() == null ? 0 : task.getTotalBooks()) + discovered >= effectiveBookLimit(task)) {
                    break;
                }
                validateBook(item, site);
                discovered++;
                String title = item.getSourceWorkTitle().trim();
                String author = normalizeAuthor(item.getAuthorName());
                String dedupeKey = buildDedupeKey(title, author);
                if (!batchDedupeKeys.add(dedupeKey)) {
                    skipped++;
                    continue;
                }
                ReaderSourceTaskBook existed = taskBookMapper.selectOne(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
                    .eq(ReaderSourceTaskBook::getTaskId, task.getId())
                    .and(query -> query.eq(ReaderSourceTaskBook::getSourceWorkUrl, item.getSourceWorkUrl().trim())
                        .or().eq(ReaderSourceTaskBook::getWorkDedupeKey, dedupeKey))
                    .last("LIMIT 1"));
                if (existed != null) {
                    skipped++;
                    continue;
                }
                ReaderWork work = findWork(dedupeKey, title, author);
                int localLatest = work == null ? 0 : latestChapterNo(work.getId());
                int remoteLatest = item.getRemoteLatestChapterNo() == null ? 0 : item.getRemoteLatestChapterNo();
                String action = work == null ? "NEW"
                    : remoteLatest <= 0 || remoteLatest > localLatest ? "INCREMENTAL" : "UNCHANGED";
                ReaderSourceTaskBook book = new ReaderSourceTaskBook();
                book.setTaskId(task.getId());
                book.setRunId(run.getId());
                book.setBatchNo(task.getBatchNo());
                book.setSourceWorkUrl(item.getSourceWorkUrl().trim());
                book.setSourceWorkTitle(trimToLength(title, 255));
                book.setAuthorName(trimToLength(author, 128));
                book.setCategoryName(trimToLength(item.getCategoryName(), 64));
                book.setSerialStatus(normalizeSerialStatus(item.getSerialStatus()));
                book.setWorkDedupeKey(dedupeKey);
                book.setWorkId(work == null ? null : work.getId());
                book.setDedupeAction(action);
                book.setStatus("UNCHANGED".equals(action) ? "SKIPPED" : "QUEUED");
                book.setLocalLatestChapterNo(localLatest);
                book.setRemoteLatestChapterNo(remoteLatest);
                book.setPlannedChapterCount("NEW".equals(action) ? Math.max(remoteLatest, item.getRemoteChapterCount() == null ? 0 : item.getRemoteChapterCount()) : Math.max(0, remoteLatest - localLatest));
                book.setProcessedChapterCount(0);
                book.setSuccessChapterCount(0);
                book.setSkippedChapterCount("UNCHANGED".equals(action) ? Math.max(0, remoteLatest) : 0);
                book.setFailureChapterCount(0);
                taskBookMapper.insert(book);
                ensureCategory(item.getCategoryName());
                accepted++;
                if ("UNCHANGED".equals(action)) unchanged++;
            }
            int total = safeAdd(task.getTotalBooks(), accepted + skipped);
            task.setTotalBooks(total);
            int discoveredSkipped = skipped + unchanged;
            task.setProcessedBooks(safeAdd(task.getProcessedBooks(), discoveredSkipped));
            task.setSkippedBooks(safeAdd(task.getSkippedBooks(), discoveredSkipped));
            task.setProgressPercent(total == 0 ? 0 : Math.min(100, task.getProcessedBooks() * 100 / total));
            if (accepted > 0 || discoveredSkipped > 0) {
                clearFailureState(task);
            }
            taskMapper.updateById(task);
            run.setRequestCount(safeAdd(run.getRequestCount(), 1));
            run.setSuccessCount(safeAdd(run.getSuccessCount(), accepted));
            run.setSkippedCount(safeAdd(run.getSkippedCount(), discoveredSkipped));
            run.setHeartbeatAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                // 发现完成且没有待采集书籍时直接结束，避免全量已同步任务永久停在运行中。
                boolean hasQueuedBooks = countQueuedBooks(task.getId()) > 0;
                if (hasQueuedBooks) {
                    task.setStatus(RUNNING);
                } else {
                    run.setStatus(COMPLETED);
                    run.setFinishedAt(LocalDateTime.now());
                    task.setStatus(hasWaitingReviewBooks(task.getId()) ? WAITING_REVIEW : COMPLETED);
                    task.setProgressPercent(100);
                }
            }
            taskRunMapper.updateById(run);
            coordinator.markBatchProcessed(run.getId(), "books:" + bo.getBatchId());
            writeLog(task.getId(), run.getId(), null, "INFO", "DISCOVERY", "已接收书籍发现结果",
                "{\"discovered\":" + discovered + ",\"accepted\":" + accepted
                    + ",\"skipped\":" + discoveredSkipped + ",\"completed\":" + Boolean.TRUE.equals(bo.getCompleted()) + "}");
            if (!RUNNING.equals(run.getStatus())) {
                releaseLeases(task, run, bo.getWorkerId());
            }
            return toAck(run, task, accepted, skipped);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("书籍发现结果处理被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /** 从当前任务中按顺序领取下一本排队书籍。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderSourceWorkerTaskVo claimNextBook(Long runId, String runToken, String workerId) {
        ReaderSourceTaskRun run = requireOwnedRun(runId, runToken, workerId);
        ReaderSourceTask task = requireTask(run.getTaskId());
        if (isSingleTask(task)) {
            throw new ServiceException("单本采集任务不支持领取批量书籍");
        }
        ReaderSourceSite site = requireSite(task.getSiteId());
        ReaderSourceRule rule = requireRule(task.getRuleId());
        ReaderSourcePolicy policy = requirePolicy(task.getPolicyId());
        List<ReaderSourceTaskBook> runningBooks = taskBookMapper.selectList(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
            .eq(ReaderSourceTaskBook::getTaskId, task.getId()).eq(ReaderSourceTaskBook::getRunId, run.getId())
            .eq(ReaderSourceTaskBook::getStatus, "RUNNING")
            .orderByAsc(ReaderSourceTaskBook::getUpdateTime).orderByAsc(ReaderSourceTaskBook::getId));
        // A batch run processes one book at a time. Old workers may have left
        // multiple RUNNING rows behind after a lost lease; resume the oldest one
        // and return the extras to the queue instead of creating more concurrent work.
        ReaderSourceTaskBook book = runningBooks.isEmpty() ? null : runningBooks.getFirst();
        for (int index = 1; index < runningBooks.size(); index++) {
            ReaderSourceTaskBook duplicate = runningBooks.get(index);
            duplicate.setStatus("QUEUED");
            duplicate.setRunId(null);
            duplicate.setStartedAt(null);
            taskBookMapper.updateById(duplicate);
        }
        if (book == null) {
            book = taskBookMapper.selectOne(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
                .eq(ReaderSourceTaskBook::getTaskId, task.getId()).eq(ReaderSourceTaskBook::getStatus, "QUEUED")
                .orderByAsc(ReaderSourceTaskBook::getId).last("LIMIT 1"));
        }
        if (book == null) return null;
        if (!"RUNNING".equals(book.getStatus())) {
            book.setStatus("RUNNING");
            book.setRunId(run.getId());
            book.setStartedAt(LocalDateTime.now());
            taskBookMapper.updateById(book);
        }
        ReaderSourceWorkerTaskVo vo = toTaskVo(task, run, rule, policy, workerId);
        vo.setTaskBookId(book.getId());
        vo.setWorkId(book.getWorkId());
        vo.setSourceWorkUrl(book.getSourceWorkUrl());
        vo.setSourceWorkTitle(book.getSourceWorkTitle());
        vo.setAuthorName(book.getAuthorName());
        vo.setCursorChapterNo(book.getLocalLatestChapterNo());
        vo.setStartChapterNo(Math.max(1, book.getLocalLatestChapterNo() + 1));
        // 空结束章节表示采集到来源目录末尾，不能用发现阶段的摘要章号截断整本书。
        if (task.getEndChapterNo() != null) {
            vo.setEndChapterNo(task.getEndChapterNo());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int materializePending(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        List<ReaderSourceChapterSnapshot> snapshots = snapshotMapper.selectList(Wrappers.<ReaderSourceChapterSnapshot>lambdaQuery()
            .eq(ReaderSourceChapterSnapshot::getTaskId, taskId)
            .in(ReaderSourceChapterSnapshot::getSnapshotStatus, List.of("NEW", "CHANGED"))
            .orderByAsc(ReaderSourceChapterSnapshot::getChapterNo)
            .orderByAsc(ReaderSourceChapterSnapshot::getId));
        int materialized = 0;
        for (ReaderSourceChapterSnapshot snapshot : snapshots) {
            ReaderSourceTaskBook taskBook = snapshot.getTaskBookId() == null ? null : taskBookMapper.selectById(snapshot.getTaskBookId());
            ReaderWork work = ensureCollectionWork(task, taskBook, null);
            snapshot.setWorkId(work.getId());
            snapshotMapper.updateById(snapshot);
            upsertCollectedChapter(work.getId(), snapshot);
            materialized++;
        }
        return materialized;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptError(ReaderSourceWorkerErrorBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getErrorType()) || StringUtils.isBlank(bo.getMessage())) {
            throw new ServiceException("错误类型和错误摘要不能为空");
        }
        ReaderSourceTaskRun run = requireOwnedRun(bo.getRunId(), bo.getRunToken(), bo.getWorkerId());
        ReaderSourceTask task = requireTask(run.getTaskId());
        ReaderSourceSite site = requireSite(task.getSiteId());
        String errorType = bo.getErrorType().trim().toUpperCase(Locale.ROOT);
        if (!ERROR_TYPES.contains(errorType)) {
            throw new ServiceException("不支持的采集错误类型");
        }
        if (bo.getHttpStatus() != null && (bo.getHttpStatus() < 100 || bo.getHttpStatus() > 599)) {
            throw new ServiceException("HTTP 状态码不合法");
        }
        ReaderSourceError error = new ReaderSourceError();
        error.setTaskId(task.getId());
        error.setRunId(run.getId());
        error.setErrorType(errorType);
        error.setHttpStatus(bo.getHttpStatus());
        error.setSourceUrl(StringUtils.isBlank(bo.getSourceUrl()) ? null : sanitizeUrl(bo.getSourceUrl(), site.getAllowedHost()));
        String message = trimToLength(bo.getMessage().replaceAll("[\\r\\n\\t]+", " "), 2000);
        error.setMessage(message);
        error.setRetryAt(bo.getRetryAt());
        error.setResolved("0");
        errorMapper.insert(error);

        run.setFailureCount(safeAdd(run.getFailureCount(), 1));
        if (Integer.valueOf(429).equals(bo.getHttpStatus())) {
            run.setTooManyRequestsCount(safeAdd(run.getTooManyRequestsCount(), 1));
        }
        run.setErrorMessage(error.getMessage());
        run.setHeartbeatAt(LocalDateTime.now());
        List<ReaderSourceTaskBook> requeuedBooks = requeueRunningBooks(run.getId(), message);
        boolean dailyLimit = message.contains("每日请求上限");
        boolean rateLimitWait = "RATE_LIMIT".equals(errorType) || message.startsWith("RATE_LIMIT_WAIT:");
        boolean circuitOpen = dailyLimit || rateLimitWait ? false
            : coordinator.recordFailure(site.getId(), requirePolicy(task.getPolicyId()).getCircuitBreakerThreshold());
        if (Integer.valueOf(401).equals(bo.getHttpStatus()) || Integer.valueOf(403).equals(bo.getHttpStatus())) {
            run.setStatus(PAUSED);
            task.setStatus(PAUSED);
            task.setFailureCode("HTTP_" + bo.getHttpStatus());
            task.setRetryAfter(null);
            task.setFailReason("站点返回 " + bo.getHttpStatus() + "，已暂停；如已配置备用书源将自动创建续采任务");
        } else if (dailyLimit) {
            run.setStatus(PAUSED);
            task.setStatus(PAUSED);
            task.setFailureCode("DAILY_LIMIT");
            task.setRetryAfter(null);
            task.setFailReason("站点每日请求额度已耗尽，任务已暂停；额度恢复后可继续执行");
        } else if (rateLimitWait) {
            run.setStatus(PAUSED);
            run.setFinishedAt(LocalDateTime.now());
            task.setStatus(RUNNING);
            task.setFailureCode("RATE_LIMIT");
            task.setFailReason("站点分钟限流，已自动让出执行槽并安排下一轮继续");
        } else if (circuitOpen) {
            run.setStatus(FAILED);
            run.setFinishedAt(LocalDateTime.now());
            run.setCircuitOpen("1");
            task.setStatus(FAILED);
            task.setFailureCode("CIRCUIT_OPEN");
            task.setFailReason("连续失败达到熔断阈值，已暂停采集");
        } else {
            // 可恢复错误进入有界自动重试；达到上限后才暂停，避免任务长期假运行或无限重试。
            int used = task.getAutoRetryCount() == null ? 0 : task.getAutoRetryCount();
            int maxRetries = task.getMaxAutoRetryCount() == null ? 3 : task.getMaxAutoRetryCount();
            if (ENABLED.equals(task.getAutoRetryEnabled()) && used < maxRetries) {
                run.setStatus(PAUSED);
                run.setFinishedAt(LocalDateTime.now());
                task.setStatus(PAUSED);
                task.setFailureCode(normalizeFailureCode(errorType, bo.getHttpStatus()));
                task.setRetryAfter(LocalDateTime.now().plusSeconds(Math.min(300, 30L * (used + 1))));
                task.setFailReason("等待自动重试（" + (used + 1) + "/" + maxRetries + "）：" + trimToLength(message, 700));
            } else {
                run.setStatus(PAUSED);
                task.setStatus(PAUSED);
                task.setFailureCode(normalizeFailureCode(errorType, bo.getHttpStatus()));
                task.setRetryAfter(null);
                task.setFailReason("自动重试次数已用尽，已暂停：" + trimToLength(message, 700));
            }
        }
        taskRunMapper.updateById(run);
        taskMapper.updateById(task);
        if (!RUNNING.equals(run.getStatus())) {
            releaseLeases(task, run, bo.getWorkerId());
        }
        if (rateLimitWait && RUNNING.equals(task.getStatus()) && latestRunningRun(task.getId()) == null) {
            createReplacementRun(task, run, "RATE_LIMIT", "分钟限流等待后自动续采", LocalDateTime.now());
        }
        writeLog(task.getId(), run.getId(), requeuedBooks.isEmpty() ? null : requeuedBooks.getFirst().getId(),
            "ERROR", "FETCH", "Worker 报告采集异常：" + trimToLength(message, 800), "{\"errorType\":\"" + errorType + "\",\"httpStatus\":" + bo.getHttpStatus() + "}");
        if (isFallbackEligibleFailure(bo)) {
            sourceService.autoProvisionTaskFallbacks(rootTask(task).getId());
            createFallbackTasks(task, requeuedBooks);
        }
    }

    private boolean isFallbackEligibleFailure(ReaderSourceWorkerErrorBo bo) {
        Integer status = bo == null ? null : bo.getHttpStatus();
        return Integer.valueOf(401).equals(status) || Integer.valueOf(403).equals(status)
            || (status != null && status >= 500 && status <= 599);
    }

    private void createReplacementRun(ReaderSourceTask task, ReaderSourceTaskRun previous,
                                      String triggerType, String triggerReason, LocalDateTime now) {
        ReaderSourceTaskRun replacement = new ReaderSourceTaskRun();
        replacement.setTaskId(task.getId());
        replacement.setRunToken(UUID.randomUUID().toString());
        replacement.setExecutorType(task.getExecutorType());
        replacement.setStatus(RUNNING);
        replacement.setStartedAt(now);
        replacement.setHeartbeatAt(now);
        replacement.setRequestCount(0);
        replacement.setSuccessCount(0);
        replacement.setSkippedCount(0);
        replacement.setFailureCount(0);
        replacement.setTooManyRequestsCount(0);
        replacement.setCircuitOpen("0");
        replacement.setRetryNo(safeAdd(previous.getRetryNo(), 1));
        replacement.setTriggerType(triggerType);
        replacement.setTriggerReason(triggerReason);
        taskRunMapper.insert(replacement);
        task.setLastRunAt(now);
        taskMapper.updateById(task);
    }

    /** Worker 报错后释放当前书籍，避免 RUNNING 明细永久阻塞后续重试。 */
    private List<ReaderSourceTaskBook> requeueRunningBooks(Long runId, String message) {
        List<ReaderSourceTaskBook> books = taskBookMapper.selectList(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
            .eq(ReaderSourceTaskBook::getRunId, runId)
            .eq(ReaderSourceTaskBook::getStatus, "RUNNING"));
        for (ReaderSourceTaskBook book : books) {
            book.setStatus("QUEUED");
            book.setRunId(null);
            book.setStartedAt(null);
            book.setFinishedAt(null);
            book.setLastError(trimToLength(message, 2000));
            taskBookMapper.updateById(book);
        }
        return books;
    }

    /**
     * 401/403 只允许切换到预先配置、已授权且启用的备用站点。
     * 每个未完成的原任务书籍最多生成一个对应路由的单本续采任务，子任务从本地最新章节之后继续。
     */
    private void createFallbackTasks(ReaderSourceTask parentTask, List<ReaderSourceTaskBook> books) {
        ReaderSourceTask rootTask = rootTask(parentTask);
        if ((books == null || books.isEmpty()) && parentTask.getFallbackSourceTaskBookId() != null) {
            ReaderSourceTaskBook sourceBook = taskBookMapper.selectById(parentTask.getFallbackSourceTaskBookId());
            if (sourceBook != null) books = new java.util.ArrayList<>(List.of(sourceBook));
        }
        if ((books == null || books.isEmpty()) && isSingleTask(parentTask)) {
            ReaderSourceTaskBook single = new ReaderSourceTaskBook();
            single.setTaskId(rootTask.getId());
            single.setSourceWorkTitle(parentTask.getSourceWorkTitle());
            single.setLocalLatestChapterNo(Math.max(0, parentTask.getCurrentChapterNo() == null
                ? Math.max(0, safeAdd(parentTask.getStartChapterNo(), -1)) : parentTask.getCurrentChapterNo()));
            books = new java.util.ArrayList<>(List.of(single));
        }
        if (books == null || books.isEmpty()) {
            writeLog(parentTask.getId(), null, null, "WARN", "FALLBACK", "授权异常未发现可续采的任务书籍", null);
            return;
        }
        List<ReaderSourceTaskFallback> routes = fallbackMapper.selectList(Wrappers.<ReaderSourceTaskFallback>lambdaQuery()
            .eq(ReaderSourceTaskFallback::getTaskId, rootTask.getId())
            .eq(ReaderSourceTaskFallback::getAutoEnabled, ENABLED)
            .eq(ReaderSourceTaskFallback::getStatus, ENABLED)
            .orderByAsc(ReaderSourceTaskFallback::getPriority).orderByAsc(ReaderSourceTaskFallback::getId));
        if (routes.isEmpty()) {
            writeLog(parentTask.getId(), null, books.get(0).getId(), "WARN", "FALLBACK", "没有可用的其他授权站点，任务等待人工处理", "{\"reason\":\"NO_ROUTE\"}");
            return;
        }
        for (ReaderSourceTaskBook book : books) {
            ReaderSourceTaskFallback route = null;
            ReaderSourceSite site = null;
            ReaderSourceRule rule = null;
            ReaderSourcePolicy policy = null;
            for (ReaderSourceTaskFallback candidate : routes) {
                ReaderSourceSite candidateSite = siteMapper.selectById(candidate.getSiteId());
                ReaderSourceRule candidateRule = ruleMapper.selectById(candidate.getRuleId());
                ReaderSourcePolicy candidatePolicy = policyMapper.selectById(candidate.getPolicyId());
                LambdaQueryWrapper<ReaderSourceTask> attemptedQuery = Wrappers.<ReaderSourceTask>lambdaQuery()
                    .eq(ReaderSourceTask::getFallbackId, candidate.getId())
                    .notIn(ReaderSourceTask::getStatus, List.of("CANCELED"));
                if (book.getId() == null) {
                    attemptedQuery.isNull(ReaderSourceTask::getFallbackSourceTaskBookId)
                        .eq(ReaderSourceTask::getParentTaskId, parentTask.getId());
                } else {
                    attemptedQuery.eq(ReaderSourceTask::getFallbackSourceTaskBookId, book.getId());
                }
                ReaderSourceTask attempted = taskMapper.selectOne(attemptedQuery.last("LIMIT 1"));
                if (candidateSite != null && candidateRule != null && candidatePolicy != null
                    && !candidateSite.getId().equals(parentTask.getSiteId())
                    && attempted == null
                    && APPROVED.equals(candidateSite.getComplianceStatus()) && ENABLED.equals(candidateSite.getStatus())
                    && ENABLED.equals(candidateRule.getStatus()) && ENABLED.equals(candidatePolicy.getStatus())) {
                    route = candidate;
                    site = candidateSite;
                    rule = candidateRule;
                    policy = candidatePolicy;
                    break;
                }
            }
            if (route == null) {
                writeLog(parentTask.getId(), null, book.getId(), "WARN", "FALLBACK", "没有满足授权和启用条件的备用书源", "{\"reason\":\"NO_ELIGIBLE_ROUTE\"}");
                continue;
            }
            String sourceUrl = expandFallbackUrl(route.getSourceUrlTemplate(), book);
            validateSourceUrl(sourceUrl, site.getAllowedHost());
            ReaderSourceTask child = new ReaderSourceTask();
            child.setTaskName(trimToLength(parentTask.getTaskName() + " / 备用续采 / " + book.getSourceWorkTitle(), 255));
            child.setSiteId(site.getId());
            child.setRuleId(rule.getId());
            child.setPolicyId(policy.getId());
            child.setExecutorType(parentTask.getExecutorType());
            child.setSourceWorkUrl(sourceUrl);
            child.setSourceWorkTitle(book.getSourceWorkTitle());
            child.setStartChapterNo(Math.max(1, safeAdd(book.getLocalLatestChapterNo(), 1)));
            child.setEndChapterNo(parentTask.getEndChapterNo());
            child.setIncremental("1");
            child.setCollectionMode("SINGLE");
            child.setBookLimit(1);
            child.setBatchNo(parentTask.getBatchNo());
            child.setDailyRetryEnabled("1");
            child.setAutoRetryEnabled("1");
            child.setAutoRetryCount(0);
            child.setMaxAutoRetryCount(parentTask.getMaxAutoRetryCount() == null ? 3 : parentTask.getMaxAutoRetryCount());
            child.setParentTaskId(parentTask.getId());
            child.setFallbackId(route.getId());
            child.setFallbackSourceTaskBookId(book.getId());
            child.setStatus(RUNNING);
            child.setLastRunAt(LocalDateTime.now());
            taskMapper.insert(child);
            ReaderSourceTaskRun childRun = new ReaderSourceTaskRun();
            childRun.setTaskId(child.getId());
            childRun.setRunToken(UUID.randomUUID().toString());
            childRun.setExecutorType(child.getExecutorType());
            childRun.setStatus(RUNNING);
            childRun.setStartedAt(LocalDateTime.now());
            childRun.setHeartbeatAt(childRun.getStartedAt());
            childRun.setRequestCount(0);
            childRun.setSuccessCount(0);
            childRun.setSkippedCount(0);
            childRun.setFailureCount(0);
            childRun.setTooManyRequestsCount(0);
            childRun.setCircuitOpen("0");
            childRun.setRetryNo(0);
            childRun.setTriggerType("FALLBACK");
            childRun.setTriggerReason("父任务授权异常，切换备用书源续采");
            taskRunMapper.insert(childRun);
            route.setLastChildTaskId(child.getId());
            fallbackMapper.updateById(route);
            writeLog(parentTask.getId(), childRun.getId(), book.getId(), "WARN", "FALLBACK",
                "已创建备用书源续采任务", "{\"childTaskId\":" + child.getId() + ",\"routeId\":" + route.getId() + ",\"startChapterNo\":" + child.getStartChapterNo() + "}");
        }
    }

    /** 展开备用书源地址模板，并拒绝空模板或未替换的占位符。 */
    private String expandFallbackUrl(String template, ReaderSourceTaskBook book) {
        if (StringUtils.isBlank(template)) throw new ServiceException("备用书源地址模板不能为空");
        String author = StringUtils.isBlank(book.getAuthorName()) ? "" : book.getAuthorName();
        String value = template.trim()
            .replace("{title}", java.net.URLEncoder.encode(book.getSourceWorkTitle(), StandardCharsets.UTF_8))
            .replace("{author}", java.net.URLEncoder.encode(author, StandardCharsets.UTF_8));
        if (value.contains("{")) throw new ServiceException("备用书源地址模板存在未替换占位符");
        return value;
    }

    /** 沿父任务链找到主任务，保证多个备用站点按顺序共享同一套路由和书籍进度。 */
    private ReaderSourceTask rootTask(ReaderSourceTask task) {
        ReaderSourceTask current = task;
        Set<Long> visited = new HashSet<>();
        while (current.getParentTaskId() != null && visited.add(current.getId())) {
            ReaderSourceTask parent = taskMapper.selectById(current.getParentTaskId());
            if (parent == null) break;
            current = parent;
        }
        return current;
    }

    /** 将 Worker 错误转换为管理端可操作的失败分类。 */
    private String normalizeFailureCode(String errorType, Integer httpStatus) {
        if (Integer.valueOf(401).equals(httpStatus)) return "HTTP_401";
        if (Integer.valueOf(403).equals(httpStatus)) return "HTTP_403";
        if (Integer.valueOf(429).equals(httpStatus)) return "RATE_LIMIT";
        if (httpStatus != null && httpStatus >= 500 && httpStatus <= 599) return "HTTP_5XX";
        if ("TIMEOUT".equals(errorType)) return "TIMEOUT";
        if ("QUALITY".equals(errorType)) return "QUALITY";
        if ("PARSE".equals(errorType)) return "PARSE";
        if ("POLICY".equals(errorType)) return "POLICY";
        if ("SSRF".equals(errorType)) return "SSRF";
        if ("RATE_LIMIT".equals(errorType)) return "RATE_LIMIT";
        if ("HTTP".equals(errorType)) return "HTTP_ERROR";
        return StringUtils.isBlank(errorType) ? "UNKNOWN" : trimToLength(errorType, 32);
    }

    /** Worker 侧任务事件日志，写入前限制长度并只保存脱敏摘要。 */
    private void writeLog(Long taskId, Long runId, Long taskBookId, String level, String eventType,
                          String message, String detailJson) {
        ReaderSourceTaskLog log = new ReaderSourceTaskLog();
        log.setTaskId(taskId);
        log.setRunId(runId);
        log.setTaskBookId(taskBookId);
        log.setLevel(level);
        log.setEventType(eventType);
        log.setMessage(trimToLength(message, 2000));
        log.setDetailJson(trimToLength(detailJson, 4000));
        log.setEventAt(LocalDateTime.now());
        taskLogMapper.insert(log);
    }

    private void validateResultRequest(ReaderSourceWorkerResultBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getBatchId()) || bo.getRuleVersion() == null
            || bo.getItems() == null || bo.getItems().isEmpty() || bo.getItems().size() > properties.getMaxItemsPerResult()) {
            throw new ServiceException("结果批次、规则版本和章节列表不能为空，且批次不能超过限制");
        }
        if (!bo.getBatchId().matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ServiceException("结果批次标识格式不正确");
        }
        validateNonNegative(bo.getCursorChapterNo(), "章节游标");
    }

    private void validateItem(ReaderSourceWorkerItemBo item, ReaderSourceTask task, ReaderSourceTaskBook taskBook,
                              ReaderSourceSite site) {
        if (item == null || StringUtils.isBlank(item.getSourceChapterId()) || StringUtils.isBlank(item.getSourceUrl())
            || item.getChapterNo() == null || StringUtils.isBlank(item.getChapterName()) || StringUtils.isBlank(item.getContent())
            || StringUtils.isBlank(item.getContentHash())) {
            throw new ServiceException("章节结果缺少必要字段");
        }
        int startChapterNo = taskBook == null
            ? (task.getStartChapterNo() == null ? 1 : task.getStartChapterNo())
            : Math.max(1, taskBook.getLocalLatestChapterNo() + 1);
        // remoteLatestChapterNo is discovery metadata only. It may be a list summary
        // and must not override an open-ended task's full-catalog range.
        Integer endChapterNo = task.getEndChapterNo();
        if (item.getChapterNo() < startChapterNo || (endChapterNo != null && item.getChapterNo() > endChapterNo)) {
            throw new ServiceException("章节序号超出任务范围");
        }
        if (item.getContent().getBytes(StandardCharsets.UTF_8).length > properties.getMaxContentBytes()) {
            throw new ServiceException("单章节正文超过大小限制");
        }
        validateSourceUrl(item.getSourceUrl(), site.getAllowedHost());
        // The server stores the canonical paragraph format, so validate the digest
        // against the same representation used by acceptResult.
        normalizeSha256(item.getContentHash(), ReaderNovelTextFormatter.format(item.getContent()));
        normalizeOptionalHash(item.getTitleHash(), item.getChapterName());
    }

    private ReaderSourceTaskRun requireOwnedRun(Long runId, String runToken, String workerId) {
        ReaderSourceTaskRun run = requireRunIdentity(runId, runToken, workerId);
        if (!coordinator.ownsRun(runId, workerId)) {
            throw new ServiceException("Worker 未持有该运行记录的有效租约");
        }
        return run;
    }

    /** 心跳需要在租约失效时进入回收分支，因此不能先执行 ownsRun 校验。 */
    private ReaderSourceTaskRun requireRunIdentity(Long runId, String runToken, String workerId) {
        if (runId == null || StringUtils.isBlank(runToken) || StringUtils.isBlank(workerId)) {
            throw new ServiceException("运行记录、运行令牌和 Worker 标识不能为空");
        }
        ReaderSourceTaskRun run = taskRunMapper.selectById(runId);
        if (run == null || !runToken.equals(run.getRunToken()) || !RUNNING.equals(run.getStatus())) {
            throw new ServiceException("运行记录不存在、令牌错误或任务已不在运行中");
        }
        return run;
    }

    private ReaderSourceWorkerTaskVo toTaskVo(ReaderSourceTask task, ReaderSourceTaskRun run,
                                              ReaderSourceRule rule, ReaderSourcePolicy policy, String workerId) {
        ReaderSourceWorkerTaskVo vo = new ReaderSourceWorkerTaskVo();
        vo.setRunId(run.getId());
        vo.setTaskId(task.getId());
        vo.setSiteId(task.getSiteId());
        vo.setRuleId(rule.getId());
        vo.setRuleVersion(rule.getVersionNo());
        vo.setRunToken(run.getRunToken());
        vo.setWorkerId(workerId);
        vo.setExecutorType(task.getExecutorType());
        vo.setCollectionMode(task.getCollectionMode());
        vo.setCategoryName(task.getCategoryName());
        vo.setBookLimit(effectiveBookLimit(task));
        vo.setSourceWorkUrl(task.getSourceWorkUrl());
        vo.setSourceWorkTitle(task.getSourceWorkTitle());
        vo.setAuthorName(null);
        vo.setSearchUrlTemplate(rule.getSearchUrlTemplate());
        vo.setFallbackId(task.getFallbackId());
        vo.setCatalogUrlTemplate(rule.getCatalogUrlTemplate());
        vo.setChapterUrlTemplate(rule.getChapterUrlTemplate());
        vo.setSelectorJson(rule.getSelectorJson());
        vo.setCursorChapterNo(task.getCurrentChapterNo());
        vo.setStartChapterNo(task.getStartChapterNo());
        vo.setEndChapterNo(task.getEndChapterNo());
        vo.setConcurrencyLimit(policy.getConcurrencyLimit());
        vo.setMinDelayMs(policy.getMinDelayMs());
        vo.setMaxDelayMs(policy.getMaxDelayMs());
        vo.setRequestsPerMinute(policy.getRequestsPerMinute());
        vo.setDailyRequestLimit(policy.getDailyRequestLimit());
        vo.setConnectTimeoutMs(policy.getConnectTimeoutMs());
        vo.setReadTimeoutMs(policy.getReadTimeoutMs());
        vo.setMaxRetries(policy.getMaxRetries());
        vo.setCircuitBreakerThreshold(policy.getCircuitBreakerThreshold());
        vo.setHonorRetryAfter(policy.getHonorRetryAfter());
        vo.setClaimLeaseSeconds(properties.getClaimLeaseSeconds());
        return vo;
    }

    private ReaderSourceWorkerAckVo toAck(ReaderSourceTaskRun run, ReaderSourceTask task, int accepted, int skipped) {
        ReaderSourceWorkerAckVo vo = new ReaderSourceWorkerAckVo();
        vo.setRunId(run.getId());
        vo.setAcceptedCount(accepted);
        vo.setSkippedCount(skipped);
        vo.setCursorChapterNo(task.getCurrentChapterNo());
        vo.setRunStatus(run.getStatus());
        vo.setTaskStatus(task.getStatus());
        return vo;
    }

    /** MyBatis-Plus 默认忽略 null 字段，清理失败状态必须显式写入 SQL NULL。 */
    private void clearFailureState(ReaderSourceTask task) {
        taskMapper.update(null, Wrappers.<ReaderSourceTask>lambdaUpdate()
            .eq(ReaderSourceTask::getId, task.getId())
            .setSql("failure_code = NULL, fail_reason = NULL"));
        task.setFailureCode(null);
        task.setFailReason(null);
    }

    private ReaderSourceTaskRun latestRunningRun(Long taskId) {
        return taskRunMapper.selectOne(Wrappers.<ReaderSourceTaskRun>lambdaQuery().eq(ReaderSourceTaskRun::getTaskId, taskId)
            .eq(ReaderSourceTaskRun::getStatus, RUNNING).orderByDesc(ReaderSourceTaskRun::getCreateTime)
            .orderByDesc(ReaderSourceTaskRun::getId).last("LIMIT 1"));
    }

    private ReaderSourceTask requireTask(Long id) {
        ReaderSourceTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null) throw new ServiceException("采集任务不存在");
        return task;
    }

    private ReaderSourceSite requireSite(Long id) {
        ReaderSourceSite site = id == null ? null : siteMapper.selectById(id);
        if (site == null) throw new ServiceException("书源站点不存在");
        return site;
    }

    private ReaderSourcePolicy requirePolicy(Long id) {
        ReaderSourcePolicy policy = id == null ? null : policyMapper.selectById(id);
        if (policy == null) throw new ServiceException("访问策略不存在");
        return policy;
    }

    private ReaderSourceRule requireRule(Long id) {
        ReaderSourceRule rule = id == null ? null : ruleMapper.selectById(id);
        if (rule == null) throw new ServiceException("解析规则不存在");
        return rule;
    }

    private String normalizeExecutor(String value) {
        String executor = StringUtils.isBlank(value) ? "JAVA" : value.trim().toUpperCase(Locale.ROOT);
        if (!EXECUTORS.contains(executor)) throw new ServiceException("执行器只能选择 JAVA、PYTHON 或 GO");
        return executor;
    }

    private void validateBook(ReaderSourceWorkerBookBo item, ReaderSourceSite site) {
        if (item == null || StringUtils.isBlank(item.getSourceWorkUrl()) || StringUtils.isBlank(item.getSourceWorkTitle())) {
            throw new ServiceException("来源书籍缺少地址或标题");
        }
        validateSourceUrl(item.getSourceWorkUrl(), site.getAllowedHost());
        if (item.getRemoteLatestChapterNo() != null && item.getRemoteLatestChapterNo() < 0) {
            throw new ServiceException("来源最新章节序号不能为负数");
        }
        if (item.getRemoteChapterCount() != null && item.getRemoteChapterCount() < 0) {
            throw new ServiceException("来源章节总数不能为负数");
        }
    }

    private void updateTaskBookProgress(ReaderSourceTask task, ReaderSourceWorkerResultBo bo, int accepted, int skipped) {
        if (isSingleTask(task) || bo.getItems().isEmpty()) return;
        Long taskBookId = bo.getItems().getFirst().getTaskBookId();
        if (taskBookId == null) return;
        ReaderSourceTaskBook book = taskBookMapper.selectById(taskBookId);
        if (book == null) return;
        book.setProcessedChapterCount(safeAdd(book.getProcessedChapterCount(), bo.getItems().size()));
        book.setSuccessChapterCount(safeAdd(book.getSuccessChapterCount(), accepted));
        book.setSkippedChapterCount(safeAdd(book.getSkippedChapterCount(), skipped));
        int latestChapterNo = bo.getItems().stream().map(ReaderSourceWorkerItemBo::getChapterNo)
            .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(book.getLocalLatestChapterNo());
        book.setLocalLatestChapterNo(max(book.getLocalLatestChapterNo(), latestChapterNo));
        if (Boolean.TRUE.equals(bo.getCompleted())) {
            // An open-ended run has no configured end chapter. Reaching the
            // completed callback is the worker's proof that the full catalog
            // was traversed, so persist the observed total for progress UI.
            book.setRemoteLatestChapterNo(max(book.getRemoteLatestChapterNo(), latestChapterNo));
            book.setPlannedChapterCount(max(book.getPlannedChapterCount(), book.getProcessedChapterCount()));
            book.setStatus(accepted > 0 ? "WAITING_REVIEW" : "COMPLETED");
            book.setFinishedAt(LocalDateTime.now());
            task.setProcessedBooks(safeAdd(task.getProcessedBooks(), 1));
            if (accepted > 0) task.setSuccessBooks(safeAdd(task.getSuccessBooks(), 1));
            task.setProgressPercent(task.getTotalBooks() == null || task.getTotalBooks() == 0 ? 0
                : Math.min(100, task.getProcessedBooks() * 100 / task.getTotalBooks()));
        }
        taskBookMapper.updateById(book);
        taskMapper.updateById(task);
    }

    /** 备用子任务完成后，把新增章节进度回写到父任务的原书籍明细。 */
    private void updateFallbackSourceBook(ReaderSourceTask childTask, int accepted, int skipped, boolean completed) {
        if (!completed || childTask.getParentTaskId() == null || childTask.getFallbackSourceTaskBookId() == null) return;
        ReaderSourceTaskBook sourceBook = taskBookMapper.selectById(childTask.getFallbackSourceTaskBookId());
        ReaderSourceTask root = rootTask(childTask);
        if (sourceBook == null || root == null || !root.getId().equals(sourceBook.getTaskId())) return;
        sourceBook.setProcessedChapterCount(safeAdd(sourceBook.getProcessedChapterCount(), accepted + skipped));
        sourceBook.setSuccessChapterCount(safeAdd(sourceBook.getSuccessChapterCount(), accepted));
        sourceBook.setSkippedChapterCount(safeAdd(sourceBook.getSkippedChapterCount(), skipped));
        sourceBook.setLocalLatestChapterNo(Math.max(sourceBook.getLocalLatestChapterNo() == null ? 0 : sourceBook.getLocalLatestChapterNo(),
            childTask.getCurrentChapterNo() == null ? 0 : childTask.getCurrentChapterNo()));
        sourceBook.setStatus(accepted > 0 ? WAITING_REVIEW : "COMPLETED");
        sourceBook.setFinishedAt(LocalDateTime.now());
        sourceBook.setLastError(null);
        taskBookMapper.updateById(sourceBook);
        writeLog(childTask.getParentTaskId(), null, sourceBook.getId(), "INFO", "FALLBACK", "备用书源续采已回写原任务书籍进度",
            "{\"childTaskId\":" + childTask.getId() + ",\"accepted\":" + accepted + ",\"skipped\":" + skipped + "}");
    }

    private void applyTaskBookMetadata(ReaderSourceTaskBook book, ReaderSourceWorkerItemBo item) {
        boolean changed = false;
        if (StringUtils.isNotBlank(item.getAuthorName()) && !item.getAuthorName().trim().equals(book.getAuthorName())) {
            book.setAuthorName(trimToLength(item.getAuthorName().trim(), 128));
            changed = true;
        }
        if (StringUtils.isNotBlank(item.getCategoryName()) && !item.getCategoryName().trim().equals(book.getCategoryName())) {
            book.setCategoryName(trimToLength(item.getCategoryName().trim(), 64));
            ensureCategory(item.getCategoryName().trim());
            changed = true;
        }
        if (StringUtils.isNotBlank(item.getSerialStatus())) {
            String serialStatus = normalizeSerialStatus(item.getSerialStatus());
            if (!serialStatus.equals(book.getSerialStatus())) {
                book.setSerialStatus(serialStatus);
                changed = true;
            }
        }
        if (changed) taskBookMapper.updateById(book);
    }

    /** 统一把来源采集结果建档为本地草稿，并创建可在内容审核页看到的审核记录。 */
    private ReaderWork ensureCollectionWork(ReaderSourceTask task, ReaderSourceTaskBook taskBook, ReaderSourceWorkerItemBo item) {
        String discoveredAuthor = normalizeAuthor(item != null && StringUtils.isNotBlank(item.getAuthorName())
            ? item.getAuthorName() : taskBook == null ? null : taskBook.getAuthorName());
        String discoveredCategory = item != null && StringUtils.isNotBlank(item.getCategoryName())
            ? item.getCategoryName() : taskBook == null ? task.getCategoryName() : taskBook.getCategoryName();
        String discoveredStatus = normalizeSerialStatus(item != null && StringUtils.isNotBlank(item.getSerialStatus())
            ? item.getSerialStatus() : taskBook == null ? null : taskBook.getSerialStatus());
        if (taskBook != null && taskBook.getWorkId() != null) {
            ReaderWork linked = workMapper.selectById(taskBook.getWorkId());
            if (linked != null) {
                applyDiscoveredMetadata(linked, discoveredAuthor, discoveredCategory, discoveredStatus);
                readerCoverService.ensureGeneratedCover(linked);
                ensurePendingAudit(linked, task, taskBook);
                return linked;
            }
        }
        String title = taskBook == null ? task.getSourceWorkTitle() : taskBook.getSourceWorkTitle();
        if (StringUtils.isBlank(title)) title = task.getTaskName();
        String author = discoveredAuthor;
        String dedupeKey = buildDedupeKey(title, author);
        ReaderWork work = findWork(dedupeKey, title.trim(), author);
        if (work == null) {
            work = new ReaderWork();
            work.setWorkType(WorkType.NOVEL.name());
            work.setCategoryName(trimToLength(discoveredCategory, 64));
            work.setTitle(trimToLength(title, 255));
            work.setAuthorName(trimToLength(author, 128));
            work.setDedupeKey(dedupeKey);
            work.setIntro("由授权书源采集生成的草稿内容");
            work.setPublishStatus(PublishStatus.DRAFT.name());
            work.setSerialStatus(discoveredStatus);
            work.setSourceType("SYNC");
            work.setAllowSearch("1");
            work.setTotalChapters(0);
            work.setTotalPages(0);
            workMapper.insert(work);
        } else {
            applyDiscoveredMetadata(work, discoveredAuthor, discoveredCategory, discoveredStatus);
        }
        readerCoverService.ensureGeneratedCover(work);
        ensureCategory(work.getCategoryName());
        if (taskBook != null && taskBook.getWorkId() == null) {
            taskBook.setWorkId(work.getId());
            taskBookMapper.updateById(taskBook);
        }
        ensurePendingAudit(work, task, taskBook);
        return work;
    }

    private void applyDiscoveredMetadata(ReaderWork work, String author, String category, String serialStatus) {
        boolean changed = false;
        if (StringUtils.isNotBlank(author) && !"未知作者".equals(author)
            && (StringUtils.isBlank(work.getAuthorName()) || "未知作者".equals(work.getAuthorName()))) {
            work.setAuthorName(trimToLength(author, 128));
            changed = true;
        }
        if (StringUtils.isNotBlank(category) && !category.equals(work.getCategoryName())) {
            work.setCategoryName(trimToLength(category, 64));
            ensureCategory(category);
            changed = true;
        }
        if (!serialStatus.equals(work.getSerialStatus())) {
            work.setSerialStatus(serialStatus);
            changed = true;
        }
        if (changed && work.getId() != null) workMapper.updateById(work);
    }

    private void ensurePendingAudit(ReaderWork work, ReaderSourceTask task, ReaderSourceTaskBook taskBook) {
        ReaderContentAudit existing = contentAuditMapper.selectOne(Wrappers.<ReaderContentAudit>lambdaQuery()
            .eq(ReaderContentAudit::getWorkId, work.getId())
            .eq(ReaderContentAudit::getAuditStatus, "PENDING")
            .last("LIMIT 1"));
        if (existing != null) return;
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setWorkId(work.getId());
        audit.setSourceTaskId(task.getId());
        audit.setSourceTaskBookId(taskBook == null ? null : taskBook.getId());
        audit.setAuditStatus("PENDING");
        audit.setAuditComment("书源采集结果待人工审核");
        contentAuditMapper.insert(audit);
    }

    private void upsertCollectedChapter(Long workId, ReaderSourceChapterSnapshot snapshot) {
        ReaderNovelChapter chapter = novelChapterMapper.selectOne(Wrappers.<ReaderNovelChapter>lambdaQuery()
            .eq(ReaderNovelChapter::getWorkId, workId)
            .eq(ReaderNovelChapter::getChapterNo, snapshot.getChapterNo())
            .last("LIMIT 1"));
        if (chapter == null) {
            chapter = new ReaderNovelChapter();
            chapter.setWorkId(workId);
            chapter.setChapterNo(snapshot.getChapterNo());
            chapter.setChapterName(snapshot.getChapterName());
            chapter.setWordCount(snapshot.getContent() == null ? 0 : snapshot.getContent().length());
            chapter.setPublishStatus(PublishStatus.DRAFT.name());
            novelChapterMapper.insert(chapter);
        }
        ReaderNovelChapterContent content = novelChapterContentMapper.selectById(chapter.getId());
        if (content == null) {
            content = new ReaderNovelChapterContent();
            content.setChapterId(chapter.getId());
            content.setContent(snapshot.getContent());
            novelChapterContentMapper.insert(content);
        } else {
            content.setContent(snapshot.getContent());
            novelChapterContentMapper.updateById(content);
        }
        if (workId == null) return;
        workMapper.update(null, Wrappers.<ReaderWork>lambdaUpdate()
            .eq(ReaderWork::getId, workId)
            .set(ReaderWork::getTotalChapters, novelChapterMapper.selectCount(Wrappers.<ReaderNovelChapter>lambdaQuery()
                .eq(ReaderNovelChapter::getWorkId, workId))));
    }

    private long countQueuedBooks(Long taskId) {
        return taskBookMapper.selectCount(Wrappers.<ReaderSourceTaskBook>lambdaQuery().eq(ReaderSourceTaskBook::getTaskId, taskId)
            .in(ReaderSourceTaskBook::getStatus, List.of("QUEUED", "RUNNING")));
    }

    private void releaseLeases(ReaderSourceTask task, ReaderSourceTaskRun run, String workerId) {
        ReaderSourcePolicy policy = requirePolicy(task.getPolicyId());
        coordinator.releaseRun(run.getId(), workerId);
        coordinator.releaseSite(task.getSiteId(), run.getId(), workerId, policy.getConcurrencyLimit());
    }

    private boolean hasWaitingReviewBooks(Long taskId) {
        return taskBookMapper.selectCount(Wrappers.<ReaderSourceTaskBook>lambdaQuery().eq(ReaderSourceTaskBook::getTaskId, taskId)
            .eq(ReaderSourceTaskBook::getStatus, "WAITING_REVIEW")) > 0;
    }

    private boolean isSingleTask(ReaderSourceTask task) {
        return task.getCollectionMode() == null || "SINGLE".equalsIgnoreCase(task.getCollectionMode());
    }

    private int effectiveBookLimit(ReaderSourceTask task) {
        return isSingleTask(task) ? 1 : (task.getBookLimit() == null ? 1 : task.getBookLimit());
    }

    private ReaderWork findWork(String dedupeKey, String title, String author) {
        ReaderWork work = workMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getDedupeKey, dedupeKey).last("LIMIT 1"));
        if (work != null) return work;
        return workMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery().eq(ReaderWork::getTitle, title)
            .eq(ReaderWork::getAuthorName, author).last("LIMIT 1"));
    }

    private int latestChapterNo(Long workId) {
        if (workId == null) return 0;
        ReaderNovelChapter chapter = novelChapterMapper.selectOne(Wrappers.<ReaderNovelChapter>lambdaQuery()
            .eq(ReaderNovelChapter::getWorkId, workId).orderByDesc(ReaderNovelChapter::getChapterNo)
            .last("LIMIT 1"));
        return chapter == null || chapter.getChapterNo() == null ? 0 : chapter.getChapterNo();
    }

    private void ensureCategory(String value) {
        if (StringUtils.isBlank(value)) return;
        String normalized = normalizeText(value);
        ReaderWorkCategory category = categoryMapper.selectOne(Wrappers.<ReaderWorkCategory>lambdaQuery()
            .eq(ReaderWorkCategory::getNormalizedName, normalized).last("LIMIT 1"));
        if (category == null) {
            category = new ReaderWorkCategory();
            category.setCategoryName(trimToLength(value, 64));
            category.setNormalizedName(normalized);
            category.setSourceType("SOURCE");
            category.setStatus("1");
            categoryMapper.insert(category);
        }
    }

    private String buildDedupeKey(String title, String author) {
        return sha256(normalizeText(title) + "\u0000" + normalizeText(author));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeAuthor(String author) {
        return StringUtils.isBlank(author) ? "未知作者" : author.trim();
    }

    private String normalizeSerialStatus(String status) {
        if (StringUtils.isBlank(status)) return "ONGOING";
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (value.contains("FINISHED") || value.contains("COMPLETED") || value.contains("完结") || value.contains("全本")) {
            return "FINISHED";
        }
        return "ONGOING";
    }

    private String trimWorkerId(String value) {
        String workerId = value.trim();
        if (workerId.length() > 128 || !workerId.matches("[A-Za-z0-9._:-]+")) {
            throw new ServiceException("Worker 标识格式不正确");
        }
        return workerId;
    }

    private String normalizeSha256(String supplied, String content) {
        String expected = sha256(content);
        String actual = supplied.trim().toLowerCase(Locale.ROOT).replaceFirst("^sha256:", "");
        if (!actual.matches("[0-9a-f]{64}") || !expected.equals(actual)) {
            throw new ServiceException("章节正文哈希校验失败");
        }
        return actual;
    }

    private String normalizeOptionalHash(String supplied, String value) {
        if (StringUtils.isBlank(supplied)) return null;
        String actual = supplied.trim().toLowerCase(Locale.ROOT).replaceFirst("^sha256:", "");
        if (!actual.matches("[0-9a-f]{64}") || !sha256(value).equals(actual)) {
            throw new ServiceException("章节标题哈希校验失败");
        }
        return actual;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ServiceException("无法计算章节内容哈希");
        }
    }

    private void validateSourceUrl(String rawUrl, String allowedHost) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new ServiceException("来源地址格式不正确");
        }
        if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
            || uri.getHost() == null || !sameAuthorizedHost(uri.getHost(), allowedHost) || uri.getUserInfo() != null) {
            throw new ServiceException("章节来源地址不属于允许站点");
        }
        try {
            if (!properties.isAllowPrivateForTest()) {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || "169.254.169.254".equals(address.getHostAddress())) {
                        throw new ServiceException("章节来源地址不允许访问内网地址");
                    }
                }
            }
        } catch (UnknownHostException ex) {
            throw new ServiceException("章节来源地址主机无法解析");
        }
    }

    private String sanitizeUrl(String rawUrl, String allowedHost) {
        validateSourceUrl(rawUrl, allowedHost);
        URI uri = URI.create(rawUrl.trim());
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost() + port
            + (uri.getRawPath() == null ? "" : uri.getRawPath());
    }

    private boolean sameAuthorizedHost(String actual, String expected) {
        if (actual == null || expected == null) return false;
        String actualHost = actual.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        String expectedHost = expected.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return actualHost.equals(expectedHost)
            || actualHost.replaceFirst("^www\\.", "").equals(expectedHost.replaceFirst("^www\\.", ""));
    }

    private int metric(Integer value, int fallback) {
        if (value == null) return fallback;
        if (value < 0 || value > 100_000) throw new ServiceException("Worker 统计值超出范围");
        return value;
    }

    private int safeAdd(Integer left, int right) {
        long value = (long) (left == null ? 0 : left) + right;
        if (value > Integer.MAX_VALUE) throw new ServiceException("运行统计值溢出");
        return (int) value;
    }

    private Integer max(Integer left, Integer right) {
        if (right == null) return left;
        return left == null ? right : Math.max(left, right);
    }

    private void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) throw new ServiceException(field + "不能小于 0");
    }

    private String trimToLength(String value, int max) {
        if (value == null) return null;
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
