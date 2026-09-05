package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerErrorBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerItemBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerMetricsBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerPermitBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> EXECUTORS = Set.of("JAVA", "PYTHON", "GO");
    private static final Set<String> ERROR_TYPES = Set.of("HTTP", "TIMEOUT", "PARSE", "POLICY", "SSRF", "QUALITY", "UNKNOWN");

    private final ReaderSourceWorkerProperties properties;
    private final ReaderSourceRedisCoordinator coordinator;
    private final ReaderSourceSiteMapper siteMapper;
    private final ReaderSourcePolicyMapper policyMapper;
    private final ReaderSourceRuleMapper ruleMapper;
    private final ReaderSourceTaskMapper taskMapper;
    private final ReaderSourceTaskRunMapper taskRunMapper;
    private final ReaderSourceChapterSnapshotMapper snapshotMapper;
    private final ReaderSourceErrorMapper errorMapper;

    @Override
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
            if (run == null || !coordinator.claimRun(run.getId(), workerId)) {
                continue;
            }
            ReaderSourceSite site = siteMapper.selectById(task.getSiteId());
            ReaderSourceRule rule = ruleMapper.selectById(task.getRuleId());
            ReaderSourcePolicy policy = policyMapper.selectById(task.getPolicyId());
            if (site == null || rule == null || policy == null || !"1".equals(site.getStatus())
                || !"APPROVED".equals(site.getComplianceStatus()) || !"1".equals(policy.getStatus())) {
                coordinator.releaseRun(run.getId(), workerId);
                continue;
            }
            if (coordinator.isCircuitOpen(site.getId())) {
                coordinator.releaseRun(run.getId(), workerId);
                continue;
            }
            return toTaskVo(task, run, rule, policy, workerId);
        }
        return null;
    }

    @Override
    public void heartbeat(ReaderSourceWorkerHeartbeatBo bo) {
        ReaderSourceTaskRun run = requireOwnedRun(bo == null ? null : bo.getRunId(), bo == null ? null : bo.getRunToken(),
            bo == null ? null : bo.getWorkerId());
        if (!coordinator.renewRun(run.getId(), bo.getWorkerId())) {
            throw new ServiceException("Worker 租约已失效，请重新领取任务");
        }
        run.setHeartbeatAt(LocalDateTime.now());
        taskRunMapper.updateById(run);
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
            for (ReaderSourceWorkerItemBo item : bo.getItems()) {
                validateItem(item, task, site);
                ReaderSourceChapterSnapshot same = snapshotMapper.selectByContentHash(task.getId(), item.getSourceChapterId(),
                    normalizeSha256(item.getContentHash(), item.getContent()));
                if (same != null) {
                    skipped++;
                    continue;
                }
                ReaderSourceChapterSnapshot previous = snapshotMapper.selectLatestByChapter(task.getId(), item.getSourceChapterId());
                ReaderSourceChapterSnapshot snapshot = new ReaderSourceChapterSnapshot();
                snapshot.setTaskId(task.getId());
                snapshot.setSourceChapterId(item.getSourceChapterId().trim());
                snapshot.setSourceUrl(sanitizeUrl(item.getSourceUrl(), site.getAllowedHost()));
                snapshot.setChapterNo(item.getChapterNo());
                snapshot.setChapterName(trimToLength(item.getChapterName(), 255));
                snapshot.setContentHash(normalizeSha256(item.getContentHash(), item.getContent()));
                snapshot.setTitleHash(normalizeOptionalHash(item.getTitleHash(), item.getChapterName()));
                snapshot.setContent(item.getContent());
                snapshot.setSnapshotStatus(previous == null ? "NEW" : "CHANGED");
                snapshot.setCapturedAt(LocalDateTime.now());
                snapshotMapper.insert(snapshot);
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
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                run.setStatus(COMPLETED);
                run.setFinishedAt(LocalDateTime.now());
                task.setStatus(accepted > 0 ? WAITING_REVIEW : COMPLETED);
            }
            task.setCurrentChapterNo(max(task.getCurrentChapterNo(), bo.getCursorChapterNo()));
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                task.setFailReason(null);
            }
            taskRunMapper.updateById(run);
            taskMapper.updateById(task);
            coordinator.markBatchProcessed(run.getId(), bo.getBatchId());
            coordinator.recordSuccess(site.getId());
            if (Boolean.TRUE.equals(bo.getCompleted())) {
                coordinator.releaseRun(run.getId(), bo.getWorkerId());
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
        error.setMessage(trimToLength(bo.getMessage().replaceAll("[\\r\\n\\t]+", " "), 2000));
        error.setRetryAt(bo.getRetryAt());
        error.setResolved("0");
        errorMapper.insert(error);

        run.setFailureCount(safeAdd(run.getFailureCount(), 1));
        if (Integer.valueOf(429).equals(bo.getHttpStatus())) {
            run.setTooManyRequestsCount(safeAdd(run.getTooManyRequestsCount(), 1));
        }
        run.setErrorMessage(error.getMessage());
        run.setHeartbeatAt(LocalDateTime.now());
        boolean circuitOpen = coordinator.recordFailure(site.getId(), requirePolicy(task.getPolicyId()).getCircuitBreakerThreshold());
        if (Integer.valueOf(401).equals(bo.getHttpStatus()) || Integer.valueOf(403).equals(bo.getHttpStatus())) {
            run.setStatus(PAUSED);
            task.setStatus(PAUSED);
            task.setFailReason("站点返回 " + bo.getHttpStatus() + "，已暂停等待人工复核");
        } else if (circuitOpen) {
            run.setStatus(FAILED);
            run.setFinishedAt(LocalDateTime.now());
            run.setCircuitOpen("1");
            task.setStatus(FAILED);
            task.setFailReason("连续失败达到熔断阈值，已暂停采集");
        }
        taskRunMapper.updateById(run);
        taskMapper.updateById(task);
        if (!RUNNING.equals(run.getStatus())) {
            coordinator.releaseRun(run.getId(), bo.getWorkerId());
        }
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

    private void validateItem(ReaderSourceWorkerItemBo item, ReaderSourceTask task, ReaderSourceSite site) {
        if (item == null || StringUtils.isBlank(item.getSourceChapterId()) || StringUtils.isBlank(item.getSourceUrl())
            || item.getChapterNo() == null || StringUtils.isBlank(item.getChapterName()) || StringUtils.isBlank(item.getContent())
            || StringUtils.isBlank(item.getContentHash())) {
            throw new ServiceException("章节结果缺少必要字段");
        }
        if (item.getChapterNo() < 1 || (task.getStartChapterNo() != null && item.getChapterNo() < task.getStartChapterNo())
            || (task.getEndChapterNo() != null && item.getChapterNo() > task.getEndChapterNo())) {
            throw new ServiceException("章节序号超出任务范围");
        }
        if (item.getContent().getBytes(StandardCharsets.UTF_8).length > properties.getMaxContentBytes()) {
            throw new ServiceException("单章节正文超过大小限制");
        }
        validateSourceUrl(item.getSourceUrl(), site.getAllowedHost());
        normalizeSha256(item.getContentHash(), item.getContent());
        normalizeOptionalHash(item.getTitleHash(), item.getChapterName());
    }

    private ReaderSourceTaskRun requireOwnedRun(Long runId, String runToken, String workerId) {
        if (runId == null || StringUtils.isBlank(runToken) || StringUtils.isBlank(workerId)) {
            throw new ServiceException("运行记录、运行令牌和 Worker 标识不能为空");
        }
        ReaderSourceTaskRun run = taskRunMapper.selectById(runId);
        if (run == null || !runToken.equals(run.getRunToken()) || !RUNNING.equals(run.getStatus())) {
            throw new ServiceException("运行记录不存在、令牌错误或任务已不在运行中");
        }
        if (!coordinator.ownsRun(runId, workerId)) {
            throw new ServiceException("Worker 未持有该运行记录的有效租约");
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
        vo.setSourceWorkUrl(task.getSourceWorkUrl());
        vo.setSourceWorkTitle(task.getSourceWorkTitle());
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
            || uri.getHost() == null || !uri.getHost().equalsIgnoreCase(allowedHost) || uri.getUserInfo() != null) {
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
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost() + (uri.getRawPath() == null ? "" : uri.getRawPath());
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
}
