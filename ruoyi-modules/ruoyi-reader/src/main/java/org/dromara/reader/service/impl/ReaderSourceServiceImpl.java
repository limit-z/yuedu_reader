package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderSourceTaskFallback;
import org.dromara.reader.domain.ReaderSourceTaskLog;
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBookQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceErrorMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderSourceTaskFallbackMapper;
import org.dromara.reader.mapper.ReaderSourceTaskLogMapper;
import org.dromara.reader.service.IReaderSourceService;
import org.dromara.reader.service.cache.ReaderSourceRedisCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 书源采集中心基础服务。
 *
 * <p>该服务只负责可审计的配置和任务状态，不在请求线程直接访问第三方站点，
 * 也不向 worker 暴露数据库写入能力。</p>
 */
@RequiredArgsConstructor
@Service
public class ReaderSourceServiceImpl implements IReaderSourceService {

    private static final String APPROVED = "APPROVED";
    private static final String ENABLED = "1";
    private static final String RUNNING = "RUNNING";
    private static final String PAUSED = "PAUSED";
    private static final String CANCELED = "CANCELED";
    private static final Pattern SHA_OR_SAFE_JSON_TEXT = Pattern.compile("(?i)(<\\s*script|javascript:|eval\\s*\\()");

    private final ReaderSourceSiteMapper siteMapper;
    private final ReaderSourcePolicyMapper policyMapper;
    private final ReaderSourceRuleMapper ruleMapper;
    private final ReaderSourceTaskMapper taskMapper;
    private final ReaderSourceTaskRunMapper taskRunMapper;
    private final ReaderSourceChapterSnapshotMapper snapshotMapper;
    private final ReaderSourceErrorMapper errorMapper;
    private final ReaderSourceTaskBookMapper taskBookMapper;
    /** 备用书源路由配置。 */
    private final ReaderSourceTaskFallbackMapper fallbackMapper;
    /** 任务事件日志。 */
    private final ReaderSourceTaskLogMapper taskLogMapper;
    /** 管理员手工重试熔断任务时清理站点级熔断状态。 */
    private final ReaderSourceRedisCoordinator coordinator;

    @Override
    public PageResult<ReaderSourceSite> querySitePage(ReaderSourceSiteQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReaderSourceSite> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.isNotBlank(bo.getSiteName()), ReaderSourceSite::getSiteName, bo.getSiteName());
        wrapper.eq(StringUtils.isNotBlank(bo.getComplianceStatus()), ReaderSourceSite::getComplianceStatus, bo.getComplianceStatus());
        wrapper.eq(StringUtils.isNotBlank(bo.getStatus()), ReaderSourceSite::getStatus, bo.getStatus());
        wrapper.orderByDesc(ReaderSourceSite::getUpdateTime).orderByDesc(ReaderSourceSite::getId);
        Page<ReaderSourceSite> page = siteMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSite(ReaderSourceSiteBo bo) {
        if (StringUtils.isBlank(bo.getSiteName())) {
            throw new ServiceException("站点名称不能为空");
        }
        URI baseUri = validatePublicHttpUri(bo.getBaseUrl(), "站点地址");
        String allowedHost = normalizeHost(StringUtils.isBlank(bo.getAllowedHost()) ? baseUri.getHost() : bo.getAllowedHost());
        if (!baseUri.getHost().equalsIgnoreCase(allowedHost)) {
            throw new ServiceException("允许主机必须与站点地址主机一致");
        }
        ReaderSourceSite site = bo.getId() == null ? new ReaderSourceSite() : requireSite(bo.getId());
        boolean addressChanged = site.getId() != null
            && (!Objects.equals(site.getBaseUrl(), bo.getBaseUrl().trim())
            || !Objects.equals(site.getAllowedHost(), allowedHost));
        site.setSiteName(bo.getSiteName().trim());
        site.setBaseUrl(bo.getBaseUrl().trim());
        site.setAllowedHost(allowedHost);
        site.setAuthorizationNote(trimToLength(bo.getAuthorizationNote(), 1000));
        site.setDefaultPolicyId(bo.getDefaultPolicyId());
        site.setRemark(trimToLength(bo.getRemark(), 1000));
        if (site.getId() == null) {
            site.setComplianceStatus("UNCONFIRMED");
            site.setStatus("0");
            siteMapper.insert(site);
        } else {
            if (addressChanged) {
                // 只有改变访问地址才需要重新确认，编辑备注等普通字段不撤销管理员许可。
                site.setComplianceStatus("UNCONFIRMED");
                site.setComplianceCheckedAt(null);
                site.setComplianceCheckedBy(null);
                site.setStatus("0");
            }
            siteMapper.updateById(site);
        }
        return site.getId();
    }

    @Override
    public void checkCompliance(Long siteId, ReaderSourceComplianceBo bo) {
        ReaderSourceSite site = requireSite(siteId);
        if (bo == null || bo.getApproved() == null) {
            throw new ServiceException("请明确确认站点是否允许采集");
        }
        String authorizationNote = trimToLength(bo.getAuthorizationNote(), 1000);
        if (Boolean.TRUE.equals(bo.getApproved()) && StringUtils.isBlank(authorizationNote)) {
            throw new ServiceException("允许采集时必须填写授权来源说明");
        }
        site.setAuthorizationNote(authorizationNote);
        site.setComplianceStatus(Boolean.TRUE.equals(bo.getApproved()) ? APPROVED : "REJECTED");
        site.setComplianceCheckedAt(LocalDateTime.now());
        site.setComplianceCheckedBy(currentOperatorId());
        if (!Boolean.TRUE.equals(bo.getApproved())) {
            site.setStatus("0");
        }
        siteMapper.updateById(site);
    }

    @Override
    public void updateSiteStatus(Long siteId, boolean enabled) {
        ReaderSourceSite site = requireSite(siteId);
        if (enabled && !APPROVED.equals(site.getComplianceStatus())) {
            throw new ServiceException("站点尚未完成授权来源确认，不能启用采集许可");
        }
        site.setStatus(enabled ? ENABLED : "0");
        siteMapper.updateById(site);
    }

    @Override
    public PageResult<ReaderSourcePolicy> queryPolicyPage(String policyName, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<ReaderSourcePolicy> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.isNotBlank(policyName), ReaderSourcePolicy::getPolicyName, policyName);
        wrapper.eq(StringUtils.isNotBlank(status), ReaderSourcePolicy::getStatus, status);
        wrapper.orderByDesc(ReaderSourcePolicy::getUpdateTime).orderByDesc(ReaderSourcePolicy::getId);
        Page<ReaderSourcePolicy> page = policyMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public Long savePolicy(ReaderSourcePolicyBo bo) {
        applyPolicyDefaults(bo);
        validatePolicy(bo);
        ReaderSourcePolicy policy = bo.getId() == null ? new ReaderSourcePolicy() : requirePolicy(bo.getId());
        policy.setPolicyName(bo.getPolicyName().trim());
        policy.setConcurrencyLimit(bo.getConcurrencyLimit());
        policy.setMinDelayMs(bo.getMinDelayMs());
        policy.setMaxDelayMs(bo.getMaxDelayMs());
        policy.setRequestsPerMinute(bo.getRequestsPerMinute());
        policy.setDailyRequestLimit(bo.getDailyRequestLimit());
        policy.setConnectTimeoutMs(bo.getConnectTimeoutMs());
        policy.setReadTimeoutMs(bo.getReadTimeoutMs());
        policy.setMaxRetries(bo.getMaxRetries());
        policy.setCircuitBreakerThreshold(bo.getCircuitBreakerThreshold());
        // Retry-After is a server-provided backoff signal and cannot be disabled.
        policy.setHonorRetryAfter("1");
        policy.setRemark(trimToLength(bo.getRemark(), 1000));
        if (policy.getId() == null) {
            policy.setStatus("1");
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }
        return policy.getId();
    }

    /** 批量启用或停用限流策略。 */
    @Override
    public ReaderBatchActionResult batchPolicyStatus(List<Long> policyIds, boolean enabled) {
        return ReaderBatchActionResult.execute(policyIds, policyId -> {
            ReaderSourcePolicy policy = policyMapper.selectById(policyId);
            if (policy == null) {
                return "限流策略不存在";
            }
            String nextStatus = enabled ? "1" : "0";
            if (nextStatus.equals(policy.getStatus())) {
                return enabled ? "策略已经启用" : "策略已经停用";
            }
            policy.setStatus(nextStatus);
            policyMapper.updateById(policy);
            return null;
        });
    }

    @Override
    public PageResult<ReaderSourceRule> queryRulePage(Long siteId, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<ReaderSourceRule> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(siteId != null, ReaderSourceRule::getSiteId, siteId);
        wrapper.eq(StringUtils.isNotBlank(status), ReaderSourceRule::getStatus, status);
        wrapper.orderByDesc(ReaderSourceRule::getVersionNo).orderByDesc(ReaderSourceRule::getId);
        Page<ReaderSourceRule> page = ruleMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRule(ReaderSourceRuleBo bo) {
        requireSite(bo.getSiteId());
        validateRule(bo);
        ReaderSourceRule rule = bo.getId() == null ? new ReaderSourceRule() : requireRule(bo.getId());
        if (rule.getId() != null && !rule.getSiteId().equals(bo.getSiteId())) {
            throw new ServiceException("不能修改规则所属站点");
        }
        rule.setSiteId(bo.getSiteId());
        rule.setRuleName(bo.getRuleName().trim());
        rule.setSearchUrlTemplate(trimToLength(bo.getSearchUrlTemplate(), 1000));
        rule.setDetailUrlTemplate(trimToLength(bo.getDetailUrlTemplate(), 1000));
        rule.setCatalogUrlTemplate(trimToLength(bo.getCatalogUrlTemplate(), 1000));
        rule.setChapterUrlTemplate(trimToLength(bo.getChapterUrlTemplate(), 1000));
        rule.setSelectorJson(bo.getSelectorJson().trim());
        rule.setTestUrl(trimToLength(bo.getTestUrl(), 1000));
        rule.setRemark(trimToLength(bo.getRemark(), 1000));
        if (rule.getId() == null) {
            Integer maxVersion = ruleMapper.selectList(Wrappers.<ReaderSourceRule>lambdaQuery()
                .eq(ReaderSourceRule::getSiteId, bo.getSiteId())
                .orderByDesc(ReaderSourceRule::getVersionNo)).stream()
                .map(ReaderSourceRule::getVersionNo).filter(v -> v != null).findFirst().orElse(0);
            rule.setVersionNo(maxVersion + 1);
            rule.setStatus("0");
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule.getId();
    }

    @Override
    public void updateRuleStatus(Long ruleId, boolean enabled) {
        ReaderSourceRule rule = requireRule(ruleId);
        if (enabled) {
            ReaderSourceSite site = requireSite(rule.getSiteId());
            if (!APPROVED.equals(site.getComplianceStatus()) || !ENABLED.equals(site.getStatus())) {
                throw new ServiceException("规则所属站点必须完成授权来源确认并启用采集许可后才能发布");
            }
            rule.setStatus("1");
        } else {
            rule.setStatus("2");
        }
        ruleMapper.updateById(rule);
    }

    @Override
    public PageResult<ReaderSourceTask> queryTaskPage(ReaderSourceTaskQueryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReaderSourceTask> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.isNotBlank(bo.getTaskName()), ReaderSourceTask::getTaskName, bo.getTaskName());
        wrapper.eq(bo.getSiteId() != null, ReaderSourceTask::getSiteId, bo.getSiteId());
        wrapper.eq(StringUtils.isNotBlank(bo.getExecutorType()), ReaderSourceTask::getExecutorType, bo.getExecutorType());
        wrapper.eq(StringUtils.isNotBlank(bo.getStatus()), ReaderSourceTask::getStatus, bo.getStatus());
        wrapper.eq(StringUtils.isNotBlank(bo.getCollectionMode()), ReaderSourceTask::getCollectionMode, bo.getCollectionMode());
        wrapper.orderByDesc(ReaderSourceTask::getUpdateTime).orderByDesc(ReaderSourceTask::getId);
        Page<ReaderSourceTask> page = taskMapper.selectPage(pageQuery.build(), wrapper);
        page.getRecords().forEach(this::fillExecutionState);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public ReaderSourceTask getTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        fillExecutionState(task);
        return task;
    }

    /**
     * 任务 status 表示业务生命周期，运行记录才表示是否真正被 Worker 领取。
     * 管理端据此把“排队等待”与“实际采集中”分开，避免 RUNNING 造成误判。
     */
    private void fillExecutionState(ReaderSourceTask task) {
        ReaderSourceTaskRun run = latestRun(task.getId());
        if (run != null) {
            task.setLatestRunId(run.getId());
            task.setLatestRunStatus(run.getStatus());
            task.setLatestRunClaimedAt(run.getClaimedAt());
        }
        if (!RUNNING.equals(task.getStatus())) {
            if ("DAILY_LIMIT".equals(task.getFailureCode())) task.setExecutionState("WAITING_DAILY_LIMIT");
            else if ("CIRCUIT_OPEN".equals(task.getFailureCode()) || "HTTP_401".equals(task.getFailureCode())
                || "HTTP_403".equals(task.getFailureCode())) task.setExecutionState("WAITING_MANUAL");
            else if ("PAUSED".equals(task.getStatus())) task.setExecutionState("PAUSED");
            else task.setExecutionState(task.getStatus());
            return;
        }
        if (run == null || !RUNNING.equals(run.getStatus()) || run.getClaimedAt() == null) {
            task.setExecutionState("WAITING_WORKER");
        } else if ("RATE_LIMIT".equals(task.getFailureCode())) {
            task.setExecutionState("WAITING_RATE_LIMIT");
        } else {
            task.setExecutionState("COLLECTING");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTask(ReaderSourceTaskBo bo) {
        String collectionMode = StringUtils.isBlank(bo.getCollectionMode()) ? "SINGLE" : bo.getCollectionMode().trim().toUpperCase(Locale.ROOT);
        if (!List.of("SINGLE", "ALL", "CATEGORY").contains(collectionMode)) {
            throw new ServiceException("采集模式只能选择 SINGLE、ALL 或 CATEGORY");
        }
        if (StringUtils.isBlank(bo.getTaskName()) || bo.getSiteId() == null || bo.getRuleId() == null
            || ("SINGLE".equals(collectionMode) && StringUtils.isBlank(bo.getSourceWorkUrl()))) {
            throw new ServiceException("任务名称、站点、规则不能为空；单本采集还必须填写作品地址");
        }
        if ("CATEGORY".equals(collectionMode) && StringUtils.isBlank(bo.getCategoryName())) {
            throw new ServiceException("按分类采集必须填写来源分类名称");
        }
        int bookLimit = "SINGLE".equals(collectionMode) ? 1 : (bo.getBookLimit() == null ? 1 : bo.getBookLimit());
        if (bookLimit < 1 || bookLimit > 10_000) {
            throw new ServiceException("书籍数量限制必须在 1 到 10000 之间");
        }
        ReaderSourceSite site = requireSite(bo.getSiteId());
        if (!APPROVED.equals(site.getComplianceStatus()) || !ENABLED.equals(site.getStatus())) {
            throw new ServiceException("站点必须完成授权来源确认并启用采集许可后才能创建采集任务");
        }
        ReaderSourceRule rule = requireRule(bo.getRuleId());
        if (!bo.getSiteId().equals(rule.getSiteId()) || !"1".equals(rule.getStatus())) {
            throw new ServiceException("规则不存在、未启用或不属于当前站点");
        }
        Long policyId = bo.getPolicyId() == null ? site.getDefaultPolicyId() : bo.getPolicyId();
        ReaderSourcePolicy policy = requirePolicy(policyId);
        if (!ENABLED.equals(policy.getStatus())) {
            throw new ServiceException("访问策略已停用");
        }
        String sourceWorkUrl = StringUtils.isBlank(bo.getSourceWorkUrl()) ? site.getBaseUrl() : bo.getSourceWorkUrl().trim();
        validateSourceUrl(sourceWorkUrl, site.getAllowedHost(), "作品地址");
        String executor = StringUtils.isBlank(bo.getExecutorType()) ? "JAVA" : bo.getExecutorType().toUpperCase(Locale.ROOT);
        if (!List.of("JAVA", "PYTHON", "GO").contains(executor)) {
            throw new ServiceException("执行器只能选择 JAVA、PYTHON 或 GO");
        }
        if (bo.getStartChapterNo() != null && bo.getStartChapterNo() < 1) {
            throw new ServiceException("起始章节必须大于等于 1");
        }
        if (bo.getEndChapterNo() != null && bo.getStartChapterNo() != null && bo.getEndChapterNo() < bo.getStartChapterNo()) {
            throw new ServiceException("结束章节不能小于起始章节");
        }
        ReaderSourceTask task = bo.getId() == null ? new ReaderSourceTask() : requireTask(bo.getId());
        if (task.getId() != null && !List.of("DRAFT", "READY").contains(task.getStatus())) {
            throw new ServiceException("任务运行后不能直接编辑，请先取消任务并新建任务");
        }
        task.setTaskName(bo.getTaskName().trim());
        task.setSiteId(bo.getSiteId());
        task.setRuleId(rule.getId());
        task.setPolicyId(policy.getId());
        task.setExecutorType(executor);
        task.setSourceWorkUrl(sourceWorkUrl);
        task.setSourceWorkTitle(trimToLength(bo.getSourceWorkTitle(), 255));
        task.setStartChapterNo(bo.getStartChapterNo());
        task.setEndChapterNo(bo.getEndChapterNo());
        task.setIncremental("0".equals(bo.getIncremental()) ? "0" : "1");
        task.setCollectionMode(collectionMode);
        task.setCategoryName(trimToLength(bo.getCategoryName(), 64));
        task.setBookLimit(bookLimit);
        if (task.getBatchNo() == null) task.setBatchNo(null);
        if (task.getTotalBooks() == null) task.setTotalBooks(0);
        if (task.getProcessedBooks() == null) task.setProcessedBooks(0);
        if (task.getSuccessBooks() == null) task.setSuccessBooks(0);
        if (task.getSkippedBooks() == null) task.setSkippedBooks(0);
        if (task.getFailedBooks() == null) task.setFailedBooks(0);
        if (task.getProgressPercent() == null) task.setProgressPercent(0);
        if (task.getAutoRetryEnabled() == null) task.setAutoRetryEnabled(ENABLED);
        if (task.getAutoRetryCount() == null) task.setAutoRetryCount(0);
        if (task.getMaxAutoRetryCount() == null) task.setMaxAutoRetryCount(3);
        if (task.getId() == null) {
            task.setStatus("DRAFT");
            task.setCurrentChapterNo(bo.getStartChapterNo() == null ? 0 : bo.getStartChapterNo() - 1);
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        // 保存任务时预先登记其他已授权且具备搜索规则的站点，任务详情可立即展示完整的备用路由链。
        autoProvisionTaskFallbacks(task.getId());
        writeLog(task.getId(), null, null, "INFO", "TASK", "采集任务配置已保存", "{\"mode\":\"" + collectionMode + "\",\"bookLimit\":" + bookLimit + "}");
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!List.of("DRAFT", "READY", PAUSED).contains(task.getStatus())) {
            throw new ServiceException("当前任务状态不能启动");
        }
        autoProvisionTaskFallbacks(task.getId());
        Long runId = createRun(task);
        writeLog(taskId, runId, null, "INFO", "TASK", "任务已启动，等待 Worker 自动领取", null);
        return runId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!RUNNING.equals(task.getStatus())) {
            throw new ServiceException("当前任务不是运行中状态");
        }
        task.setStatus(PAUSED);
        taskMapper.updateById(task);
        ReaderSourceTaskRun run = latestRun(taskId);
        if (run != null && RUNNING.equals(run.getStatus())) {
            run.setStatus(PAUSED);
            run.setHeartbeatAt(LocalDateTime.now());
            taskRunMapper.updateById(run);
        }
        writeLog(taskId, run == null ? null : run.getId(), null, "INFO", "TASK", "任务已被管理员暂停", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resumeTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!PAUSED.equals(task.getStatus())) {
            throw new ServiceException("当前任务不是已暂停状态");
        }
        autoProvisionTaskFallbacks(task.getId());
        Long runId = createRun(task);
        writeLog(taskId, runId, null, "INFO", "TASK", "任务已恢复，等待 Worker 自动领取", null);
        return runId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long retryCircuitOpenTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!"CIRCUIT_OPEN".equals(task.getFailureCode())) {
            throw new ServiceException("只有失败分类为熔断的任务才能执行此操作");
        }
        if (!List.of("FAILED", PAUSED).contains(task.getStatus())) {
            throw new ServiceException("当前任务状态不能重试熔断任务");
        }
        if (latestRunningRun(task.getId()) != null) {
            throw new ServiceException("当前任务已有运行记录，不能重复重试");
        }
        autoProvisionTaskFallbacks(task.getId());
        coordinator.resetCircuit(task.getSiteId());
        Long runId = createRun(task, "CIRCUIT_RETRY", "管理员确认重置熔断后重试", task.getAutoRetryCount() == null ? 0 : task.getAutoRetryCount() + 1).getId();
        writeLog(taskId, runId, null, "WARN", "RETRY", "管理员确认重置站点熔断并重新触发采集",
            "{\"previousFailureCode\":\"CIRCUIT_OPEN\",\"resetCircuit\":true}");
        return runId;
    }

    /**
     * 每分钟运行一次，但每个任务每天最多创建一次自动重试运行记录。
     * 这样即使服务在午夜时刻短暂不可用，次日首次调度仍能补偿执行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryDailyLimitTasks() {
        LocalDate today = LocalDate.now();
        List<ReaderSourceTask> tasks = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
            .eq(ReaderSourceTask::getStatus, PAUSED)
            .eq(ReaderSourceTask::getDailyRetryEnabled, ENABLED)
            .like(ReaderSourceTask::getFailReason, "每日请求")
            .lt(ReaderSourceTask::getUpdateTime, today.atStartOfDay())
            .orderByAsc(ReaderSourceTask::getId)
            .last("FOR UPDATE"));
        int retried = 0;
        for (ReaderSourceTask task : tasks) {
            if (today.equals(task.getLastDailyRetryDate())
                || (task.getUpdateTime() != null && !task.getUpdateTime().isBefore(today.atStartOfDay()))
                || latestRunningRun(task.getId()) != null) {
                continue;
            }
            int retryNo = task.getDailyRetryCount() == null ? 1 : task.getDailyRetryCount() + 1;
            ReaderSourceTaskRun run = createRun(task, "DAILY_LIMIT", "每日额度跨日自动重试", retryNo);
            task.setDailyRetryCount(retryNo);
            task.setLastDailyRetryDate(today);
            task.setLastDailyRetryAt(run.getStartedAt());
            taskMapper.updateById(task);
            writeLog(task.getId(), run.getId(), null, "INFO", "RETRY", "每日请求额度跨日后自动重试", "{\"retryNo\":" + retryNo + "}");
            retried++;
        }
        return retried;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (CANCELED.equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
            throw new ServiceException("当前任务已经结束");
        }
        task.setStatus(CANCELED);
        task.setFailReason("管理员取消任务");
        taskMapper.updateById(task);
        ReaderSourceTaskRun run = latestRun(taskId);
        if (run != null && (RUNNING.equals(run.getStatus()) || PAUSED.equals(run.getStatus()))) {
            run.setStatus(CANCELED);
            run.setFinishedAt(LocalDateTime.now());
            taskRunMapper.updateById(run);
        }
        writeLog(taskId, run == null ? null : run.getId(), null, "WARN", "TASK", "任务已被管理员取消", null);
    }

    @Override
    public PageResult<ReaderSourceTaskRunAdminVo> queryTaskRuns(Long taskId, PageQuery pageQuery) {
        requireTask(taskId);
        Page<ReaderSourceTaskRun> page = taskRunMapper.selectPage(pageQuery.build(), Wrappers.<ReaderSourceTaskRun>lambdaQuery()
            .eq(ReaderSourceTaskRun::getTaskId, taskId)
            .orderByDesc(ReaderSourceTaskRun::getCreateTime).orderByDesc(ReaderSourceTaskRun::getId));
        List<ReaderSourceTaskRunAdminVo> rows = page.getRecords().stream().map(this::toRunVo).toList();
        return PageResult.build(rows, page.getTotal());
    }

    @Override
    public PageResult<ReaderSourceChapterSnapshot> querySnapshots(Long taskId, String status, PageQuery pageQuery) {
        requireTask(taskId);
        Page<ReaderSourceChapterSnapshot> page = snapshotMapper.selectPage(pageQuery.build(),
            Wrappers.<ReaderSourceChapterSnapshot>lambdaQuery().eq(ReaderSourceChapterSnapshot::getTaskId, taskId)
                .eq(StringUtils.isNotBlank(status), ReaderSourceChapterSnapshot::getSnapshotStatus, status)
                .orderByDesc(ReaderSourceChapterSnapshot::getChapterNo).orderByDesc(ReaderSourceChapterSnapshot::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<ReaderSourceError> queryErrors(Long taskId, String resolved, PageQuery pageQuery) {
        if (taskId != null) requireTask(taskId);
        Page<ReaderSourceError> page = errorMapper.selectPage(pageQuery.build(),
            Wrappers.<ReaderSourceError>lambdaQuery().eq(taskId != null, ReaderSourceError::getTaskId, taskId)
                .eq(StringUtils.isNotBlank(resolved), ReaderSourceError::getResolved, resolved)
                .orderByDesc(ReaderSourceError::getCreateTime).orderByDesc(ReaderSourceError::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public PageResult<ReaderSourceTaskBook> queryTaskBooks(Long taskId, ReaderSourceTaskBookQueryBo bo, PageQuery pageQuery) {
        requireTask(taskId);
        LambdaQueryWrapper<ReaderSourceTaskBook> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ReaderSourceTaskBook::getTaskId, taskId)
            .eq(StringUtils.isNotBlank(bo.getStatus()), ReaderSourceTaskBook::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getDedupeAction()), ReaderSourceTaskBook::getDedupeAction, bo.getDedupeAction())
            .and(StringUtils.isNotBlank(bo.getKeyword()), query -> query.like(ReaderSourceTaskBook::getSourceWorkTitle, bo.getKeyword())
                .or().like(ReaderSourceTaskBook::getAuthorName, bo.getKeyword()));
        wrapper.orderByAsc(ReaderSourceTaskBook::getId);
        Page<ReaderSourceTaskBook> page = taskBookMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public ReaderSourceTaskBook getTaskBook(Long taskId, Long taskBookId) {
        requireTask(taskId);
        ReaderSourceTaskBook book = taskBookMapper.selectById(taskBookId);
        if (book == null || !taskId.equals(book.getTaskId())) throw new ServiceException("任务书籍明细不存在");
        return book;
    }

    @Override
    public PageResult<ReaderSourceChapterSnapshot> queryTaskBookSnapshots(Long taskId, Long taskBookId, PageQuery pageQuery) {
        getTaskBook(taskId, taskBookId);
        Page<ReaderSourceChapterSnapshot> page = snapshotMapper.selectPage(pageQuery.build(),
            Wrappers.<ReaderSourceChapterSnapshot>lambdaQuery().eq(ReaderSourceChapterSnapshot::getTaskBookId, taskBookId)
                .orderByAsc(ReaderSourceChapterSnapshot::getChapterNo).orderByAsc(ReaderSourceChapterSnapshot::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /** 查询任务备用书源，管理端按优先级展示并用于自动续采。 */
    @Override
    public List<ReaderSourceTaskFallback> queryTaskFallbacks(Long taskId) {
        taskId = rootTaskId(taskId);
        return fallbackMapper.selectList(Wrappers.<ReaderSourceTaskFallback>lambdaQuery()
            .eq(ReaderSourceTaskFallback::getTaskId, taskId)
            .orderByAsc(ReaderSourceTaskFallback::getPriority)
            .orderByAsc(ReaderSourceTaskFallback::getId));
    }

    @Override
    public List<ReaderSourceTask> queryTaskChildren(Long taskId) {
        taskId = rootTaskId(taskId);
        List<ReaderSourceTask> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        List<Long> parentIds = new ArrayList<>(List.of(taskId));
        while (!parentIds.isEmpty()) {
            List<ReaderSourceTask> level = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
                .in(ReaderSourceTask::getParentTaskId, parentIds)
                .orderByAsc(ReaderSourceTask::getCreateTime)
                .orderByAsc(ReaderSourceTask::getId));
            parentIds = new ArrayList<>();
            for (ReaderSourceTask child : level) {
                if (child.getId() != null && visited.add(child.getId())) {
                    result.add(child);
                    parentIds.add(child.getId());
                }
            }
        }
        return result;
    }

    /**
     * 自动把其他已授权站点中具备搜索地址的启用规则登记为当前任务的备用路由。
     * 没有搜索模板的规则不会被猜测，避免生成跨站后必然失效的作品地址。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoProvisionTaskFallbacks(Long taskId) {
        taskId = rootTaskId(taskId);
        ReaderSourceTask task = requireTask(taskId);
        ReaderSourceSite primary = requireSite(task.getSiteId());
        List<ReaderSourceSite> sites = siteMapper.selectList(Wrappers.<ReaderSourceSite>lambdaQuery()
            .eq(ReaderSourceSite::getStatus, ENABLED)
            .eq(ReaderSourceSite::getComplianceStatus, APPROVED)
            .ne(ReaderSourceSite::getId, primary.getId())
            .orderByAsc(ReaderSourceSite::getId));
        int created = 0;
        int nextPriority = queryTaskFallbacks(taskId).stream()
            .map(ReaderSourceTaskFallback::getPriority).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        for (ReaderSourceSite site : sites) {
            ReaderSourcePolicy policy = site.getDefaultPolicyId() == null ? null : policyMapper.selectById(site.getDefaultPolicyId());
            if (policy == null || !ENABLED.equals(policy.getStatus())) continue;
            List<ReaderSourceRule> rules = ruleMapper.selectList(Wrappers.<ReaderSourceRule>lambdaQuery()
                .eq(ReaderSourceRule::getSiteId, site.getId())
                .eq(ReaderSourceRule::getStatus, ENABLED)
                .isNotNull(ReaderSourceRule::getSearchUrlTemplate)
                .ne(ReaderSourceRule::getSearchUrlTemplate, "")
                .orderByDesc(ReaderSourceRule::getVersionNo));
            for (ReaderSourceRule rule : rules) {
                boolean exists = !fallbackMapper.selectList(Wrappers.<ReaderSourceTaskFallback>lambdaQuery()
                    .eq(ReaderSourceTaskFallback::getTaskId, taskId)
                    .eq(ReaderSourceTaskFallback::getSiteId, site.getId())
                    .eq(ReaderSourceTaskFallback::getRuleId, rule.getId())
                    .last("LIMIT 1")).isEmpty();
                if (exists) continue;
                ReaderSourceTaskFallback route = new ReaderSourceTaskFallback();
                route.setTaskId(taskId);
                route.setPriority(nextPriority++);
                route.setSiteId(site.getId());
                route.setRuleId(rule.getId());
                route.setPolicyId(policy.getId());
                route.setSourceUrlTemplate(rule.getSearchUrlTemplate());
                route.setAutoEnabled(ENABLED);
                route.setStatus(ENABLED);
                fallbackMapper.insert(route);
                created++;
                writeLog(taskId, null, null, "INFO", "FALLBACK", "已自动登记其他授权站点为备用书源",
                    "{\"siteId\":" + site.getId() + ",\"ruleId\":" + rule.getId() + ",\"matchMode\":\"SEARCH\"}");
            }
        }
        if (created == 0) {
            writeLog(taskId, null, null, "INFO", "FALLBACK", "未发现具备搜索规则的其他授权站点",
                "{\"primarySiteId\":" + primary.getId() + "}");
        }
        return created;
    }

    /** 保存备用书源路由，并校验站点、规则和策略属于已授权启用配置。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTaskFallback(ReaderSourceTaskFallback fallback) {
        if (fallback == null || fallback.getTaskId() == null || fallback.getSiteId() == null
            || fallback.getRuleId() == null || StringUtils.isBlank(fallback.getSourceUrlTemplate())) {
            throw new ServiceException("备用书源任务、站点、规则和地址模板不能为空");
        }
        ReaderSourceTask task = requireTask(rootTaskId(fallback.getTaskId()));
        fallback.setTaskId(task.getId());
        ReaderSourceSite site = requireSite(fallback.getSiteId());
        ReaderSourceRule rule = requireRule(fallback.getRuleId());
        ReaderSourcePolicy policy = requirePolicy(fallback.getPolicyId() == null ? site.getDefaultPolicyId() : fallback.getPolicyId());
        if (!APPROVED.equals(site.getComplianceStatus()) || !ENABLED.equals(site.getStatus())
            || !fallback.getSiteId().equals(rule.getSiteId()) || !"1".equals(rule.getStatus())
            || !ENABLED.equals(policy.getStatus())) {
            throw new ServiceException("备用书源必须是已授权、已启用且规则策略有效的配置");
        }
        ReaderSourceTaskFallback entity = fallback.getId() == null ? new ReaderSourceTaskFallback() : fallbackMapper.selectById(fallback.getId());
        if (entity == null) throw new ServiceException("备用书源配置不存在");
        if (entity.getId() != null && !fallback.getTaskId().equals(entity.getTaskId())) {
            throw new ServiceException("备用书源配置不属于当前任务");
        }
        entity.setTaskId(task.getId());
        entity.setPriority(fallback.getPriority() == null ? 1 : Math.max(1, fallback.getPriority()));
        entity.setSiteId(site.getId());
        entity.setRuleId(rule.getId());
        entity.setPolicyId(policy.getId());
        entity.setSourceUrlTemplate(trimToLength(fallback.getSourceUrlTemplate(), 1000));
        entity.setAutoEnabled("0".equals(fallback.getAutoEnabled()) ? "0" : "1");
        entity.setStatus("0".equals(fallback.getStatus()) ? "0" : "1");
        if (entity.getId() == null) fallbackMapper.insert(entity); else fallbackMapper.updateById(entity);
        writeLog(task.getId(), null, null, "INFO", "FALLBACK", "备用书源路由已保存", "{\"routeId\":" + entity.getId() + "}");
        return entity.getId();
    }

    /** 查询任务全链路事件日志，按最新事件倒序返回。 */
    @Override
    public PageResult<ReaderSourceTaskLog> queryTaskLogs(Long taskId, PageQuery pageQuery) {
        requireTask(taskId);
        Page<ReaderSourceTaskLog> page = taskLogMapper.selectPage(pageQuery.build(), Wrappers.<ReaderSourceTaskLog>lambdaQuery()
            .eq(ReaderSourceTaskLog::getTaskId, taskId)
            .orderByDesc(ReaderSourceTaskLog::getEventAt).orderByDesc(ReaderSourceTaskLog::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /** 普通网络和解析异常的自动重试入口；403、授权和额度异常不会被此路径盲目重试。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryRecoverableTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<ReaderSourceTask> tasks = taskMapper.selectList(Wrappers.<ReaderSourceTask>lambdaQuery()
            .eq(ReaderSourceTask::getStatus, PAUSED)
            .eq(ReaderSourceTask::getAutoRetryEnabled, ENABLED)
            .isNotNull(ReaderSourceTask::getRetryAfter)
            .le(ReaderSourceTask::getRetryAfter, now)
            // 安全、授权和策略阻断必须等待配置修复或人工处理，不能被普通异常重试反复触发。
            .notIn(ReaderSourceTask::getFailureCode,
                List.of("DAILY_LIMIT", "HTTP_401", "HTTP_403", "SSRF", "POLICY", "CIRCUIT_OPEN"))
            .orderByAsc(ReaderSourceTask::getRetryAfter).last("FOR UPDATE"));
        int retried = 0;
        for (ReaderSourceTask task : tasks) {
            int used = task.getAutoRetryCount() == null ? 0 : task.getAutoRetryCount();
            int max = task.getMaxAutoRetryCount() == null ? 3 : task.getMaxAutoRetryCount();
            if (used >= max || latestRunningRun(task.getId()) != null) continue;
            String failureCode = task.getFailureCode();
            ReaderSourceTaskRun run = createRun(task, "AUTO_RETRY", "可恢复异常自动续采：" + failureCode, used + 1);
            task.setAutoRetryCount(used + 1);
            task.setRetryAfter(null);
            task.setFailReason("自动重试中：" + failureCode);
            taskMapper.updateById(task);
            writeLog(task.getId(), run.getId(), null, "INFO", "RETRY", "已创建自动续采运行", "{\"retryNo\":" + (used + 1) + ",\"failureCode\":\"" + failureCode + "\"}");
            retried++;
        }
        return retried;
    }

    /** 写入任务事件，所有消息只保存脱敏摘要，避免把凭据或完整响应写入日志。 */
    void writeLog(Long taskId, Long runId, Long taskBookId, String level, String eventType, String message, String detailJson) {
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

    private Long rootTaskId(Long taskId) {
        ReaderSourceTask current = requireTask(taskId);
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (current.getParentTaskId() != null && visited.add(current.getId())) {
            ReaderSourceTask parent = taskMapper.selectById(current.getParentTaskId());
            if (parent == null) break;
            current = parent;
        }
        return current.getId();
    }

    private Long createRun(ReaderSourceTask task) {
        return createRun(task, "MANUAL", "管理员启动或恢复任务", 0).getId();
    }

    private ReaderSourceTaskRun createRun(ReaderSourceTask task, String triggerType, String triggerReason, int retryNo) {
        ReaderSourceTaskRun run = new ReaderSourceTaskRun();
        run.setTaskId(task.getId());
        run.setRunToken(UUID.randomUUID().toString());
        run.setExecutorType(task.getExecutorType());
        run.setStatus(RUNNING);
        run.setStartedAt(LocalDateTime.now());
        run.setHeartbeatAt(run.getStartedAt());
        run.setRequestCount(0);
        run.setSuccessCount(0);
        run.setSkippedCount(0);
        run.setFailureCount(0);
        run.setTooManyRequestsCount(0);
        run.setCircuitOpen("0");
        run.setRetryNo(retryNo);
        run.setTriggerType(triggerType);
        run.setTriggerReason(triggerReason);
        taskRunMapper.insert(run);
        task.setStatus(RUNNING);
        task.setLastRunAt(run.getStartedAt());
        // 启动新的执行尝试后清除上一次运行的失败标记；完整错误历史仍保留在错误记录和任务日志中。
        clearFailureState(task);
        taskMapper.updateById(task);
        return run;
    }

    /** MyBatis-Plus 默认忽略 null 字段，清理失败状态必须显式写入 SQL NULL。 */
    private void clearFailureState(ReaderSourceTask task) {
        taskMapper.update(null, Wrappers.<ReaderSourceTask>lambdaUpdate()
            .eq(ReaderSourceTask::getId, task.getId())
            .setSql("failure_code = NULL, fail_reason = NULL"));
        task.setFailureCode(null);
        task.setFailReason(null);
    }

    private void validatePolicy(ReaderSourcePolicyBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getPolicyName())) {
            throw new ServiceException("策略名称不能为空");
        }
        requireRange(bo.getConcurrencyLimit(), 1, 32, "并发数");
        requireRange(bo.getMinDelayMs(), 1000, 86_400_000, "最小请求间隔");
        requireRange(bo.getMaxDelayMs(), bo.getMinDelayMs(), 86_400_000, "最大请求间隔");
        requireRange(bo.getRequestsPerMinute(), 1, 600, "每分钟请求数");
        requireRange(bo.getDailyRequestLimit(), 1, 1_000_000, "每日请求数");
        requireRange(bo.getConnectTimeoutMs(), 1000, 120_000, "连接超时");
        requireRange(bo.getReadTimeoutMs(), 1000, 300_000, "读取超时");
        requireRange(bo.getMaxRetries(), 0, 5, "重试次数");
        requireRange(bo.getCircuitBreakerThreshold(), 1, 100, "熔断阈值");
        if ("0".equals(bo.getHonorRetryAfter())) {
            throw new ServiceException("Retry-After 必须遵循，不能关闭");
        }
    }

    private void applyPolicyDefaults(ReaderSourcePolicyBo bo) {
        if (bo == null) {
            return;
        }
        if (bo.getConcurrencyLimit() == null) bo.setConcurrencyLimit(1);
        if (bo.getMinDelayMs() == null) bo.setMinDelayMs(3000);
        if (bo.getMaxDelayMs() == null) bo.setMaxDelayMs(Math.max(8000, bo.getMinDelayMs()));
        if (bo.getRequestsPerMinute() == null) bo.setRequestsPerMinute(10);
        if (bo.getDailyRequestLimit() == null) bo.setDailyRequestLimit(1000);
        if (bo.getConnectTimeoutMs() == null) bo.setConnectTimeoutMs(10000);
        if (bo.getReadTimeoutMs() == null) bo.setReadTimeoutMs(20000);
        if (bo.getMaxRetries() == null) bo.setMaxRetries(2);
        if (bo.getCircuitBreakerThreshold() == null) bo.setCircuitBreakerThreshold(5);
    }

    private void validateRule(ReaderSourceRuleBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getRuleName()) || StringUtils.isBlank(bo.getSelectorJson())) {
            throw new ServiceException("规则名称和选择器 JSON 不能为空");
        }
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(bo.getSelectorJson());
        } catch (Exception ex) {
            throw new ServiceException("选择器必须是合法 JSON");
        }
        if (SHA_OR_SAFE_JSON_TEXT.matcher(bo.getSelectorJson()).find()) {
            throw new ServiceException("解析规则不允许脚本、javascript 或 eval 内容");
        }
        validateOptionalTemplate(bo.getSearchUrlTemplate(), "搜索地址模板");
        validateOptionalTemplate(bo.getDetailUrlTemplate(), "详情地址模板");
        validateOptionalTemplate(bo.getCatalogUrlTemplate(), "目录地址模板");
        validateOptionalTemplate(bo.getChapterUrlTemplate(), "章节地址模板");
        if (StringUtils.isNotBlank(bo.getTestUrl())) {
            validatePublicHttpUri(bo.getTestUrl(), "规则测试地址");
        }
    }

    private void validateOptionalTemplate(String value, String field) {
        if (StringUtils.isNotBlank(value) && (value.length() > 1000 || SHA_OR_SAFE_JSON_TEXT.matcher(value).find())) {
            throw new ServiceException(field + "包含不允许的内容");
        }
    }

    private URI validatePublicHttpUri(String rawUrl, String field) {
        if (StringUtils.isBlank(rawUrl)) {
            throw new ServiceException(field + "不能为空");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            if (uri.getScheme() == null || !List.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                || StringUtils.isBlank(uri.getHost()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
                throw new ServiceException(field + "仅允许不携带凭据的 HTTP/HTTPS 公网地址");
            }
            rejectPrivateAddress(uri.getHost(), field);
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(field + "格式不正确");
        }
    }

    private void validateSourceUrl(String rawUrl, String allowedHost, String field) {
        URI uri = validatePublicHttpUri(rawUrl, field);
        if (!normalizeHost(uri.getHost()).equals(normalizeHost(allowedHost))) {
            throw new ServiceException(field + "必须属于已允许的站点主机");
        }
    }

    private void rejectPrivateAddress(String host, String field) {
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost") || normalized.endsWith(".local")) {
            throw new ServiceException(field + "不允许访问本地主机");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || "169.254.169.254".equals(address.getHostAddress())) {
                    throw new ServiceException(field + "不允许访问内网、保留地址或云元数据地址");
                }
            }
        } catch (UnknownHostException ex) {
            throw new ServiceException(field + "主机无法解析");
        }
    }

    private ReaderSourceSite requireSite(Long id) {
        ReaderSourceSite site = id == null ? null : siteMapper.selectById(id);
        if (site == null) {
            throw new ServiceException("书源站点不存在");
        }
        return site;
    }

    private ReaderSourcePolicy requirePolicy(Long id) {
        if (id == null) {
            throw new ServiceException("访问策略不能为空");
        }
        ReaderSourcePolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new ServiceException("访问策略不存在");
        }
        return policy;
    }

    private ReaderSourceRule requireRule(Long id) {
        ReaderSourceRule rule = id == null ? null : ruleMapper.selectById(id);
        if (rule == null) {
            throw new ServiceException("解析规则不存在");
        }
        return rule;
    }

    private ReaderSourceTask requireTask(Long id) {
        ReaderSourceTask task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw new ServiceException("采集任务不存在");
        }
        return task;
    }

    private ReaderSourceTaskRun latestRun(Long taskId) {
        return taskRunMapper.selectOne(Wrappers.<ReaderSourceTaskRun>lambdaQuery()
            .eq(ReaderSourceTaskRun::getTaskId, taskId)
            .orderByDesc(ReaderSourceTaskRun::getCreateTime).orderByDesc(ReaderSourceTaskRun::getId)
            .last("LIMIT 1"));
    }

    private ReaderSourceTaskRun latestRunningRun(Long taskId) {
        return taskRunMapper.selectOne(Wrappers.<ReaderSourceTaskRun>lambdaQuery()
            .eq(ReaderSourceTaskRun::getTaskId, taskId)
            .eq(ReaderSourceTaskRun::getStatus, RUNNING)
            .orderByDesc(ReaderSourceTaskRun::getCreateTime).orderByDesc(ReaderSourceTaskRun::getId)
            .last("LIMIT 1"));
    }

    private ReaderSourceTaskRunAdminVo toRunVo(ReaderSourceTaskRun run) {
        ReaderSourceTaskRunAdminVo vo = new ReaderSourceTaskRunAdminVo();
        vo.setId(run.getId());
        vo.setTaskId(run.getTaskId());
        vo.setExecutorType(run.getExecutorType());
        vo.setStatus(run.getStatus());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setHeartbeatAt(run.getHeartbeatAt());
        vo.setClaimedAt(run.getClaimedAt());
        vo.setRequestCount(run.getRequestCount());
        vo.setSuccessCount(run.getSuccessCount());
        vo.setSkippedCount(run.getSkippedCount());
        vo.setFailureCount(run.getFailureCount());
        vo.setTooManyRequestsCount(run.getTooManyRequestsCount());
        vo.setCircuitOpen(run.getCircuitOpen());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setResultSummary(run.getResultSummary());
        vo.setRetryNo(run.getRetryNo());
        vo.setTriggerType(run.getTriggerType());
        vo.setTriggerReason(run.getTriggerReason());
        vo.setCreateTime(run.getCreateTime());
        vo.setUpdateTime(run.getUpdateTime());
        return vo;
    }

    private void requireRange(Integer value, int min, Integer max, String field) {
        if (value == null || value < min || value > max) {
            throw new ServiceException(field + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private String normalizeHost(String host) {
        return host == null ? null : host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    }

    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private Long currentOperatorId() {
        try {
            return org.dromara.common.satoken.utils.LoginHelper.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }
}
