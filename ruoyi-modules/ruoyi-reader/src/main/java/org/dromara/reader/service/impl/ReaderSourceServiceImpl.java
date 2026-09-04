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
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceErrorMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.service.IReaderSourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
            // 改地址后必须重新确认授权，避免旧的合规确认被错误沿用。
            site.setComplianceStatus("UNCONFIRMED");
            site.setComplianceCheckedAt(null);
            site.setComplianceCheckedBy(null);
            site.setStatus("0");
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
        site.setAuthorizationNote(trimToLength(bo.getAuthorizationNote(), 1000));
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
            throw new ServiceException("站点尚未通过合规确认，不能启用");
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
        policy.setHonorRetryAfter("0".equals(bo.getHonorRetryAfter()) ? "0" : "1");
        policy.setRemark(trimToLength(bo.getRemark(), 1000));
        if (policy.getId() == null) {
            policy.setStatus("1");
            policyMapper.insert(policy);
        } else {
            policyMapper.updateById(policy);
        }
        return policy.getId();
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
                throw new ServiceException("规则所属站点必须通过合规确认并启用后才能发布规则");
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
        wrapper.orderByDesc(ReaderSourceTask::getUpdateTime).orderByDesc(ReaderSourceTask::getId);
        Page<ReaderSourceTask> page = taskMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public ReaderSourceTask getTask(Long taskId) {
        return requireTask(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveTask(ReaderSourceTaskBo bo) {
        if (StringUtils.isBlank(bo.getTaskName()) || bo.getSiteId() == null || bo.getRuleId() == null
            || bo.getSourceWorkUrl() == null) {
            throw new ServiceException("任务名称、站点、规则和作品地址不能为空");
        }
        ReaderSourceSite site = requireSite(bo.getSiteId());
        if (!APPROVED.equals(site.getComplianceStatus()) || !ENABLED.equals(site.getStatus())) {
            throw new ServiceException("站点必须通过合规确认并启用后才能创建采集任务");
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
        validateSourceUrl(bo.getSourceWorkUrl(), site.getAllowedHost(), "作品地址");
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
        task.setSourceWorkUrl(bo.getSourceWorkUrl().trim());
        task.setSourceWorkTitle(trimToLength(bo.getSourceWorkTitle(), 255));
        task.setStartChapterNo(bo.getStartChapterNo());
        task.setEndChapterNo(bo.getEndChapterNo());
        task.setIncremental("0".equals(bo.getIncremental()) ? "0" : "1");
        if (task.getId() == null) {
            task.setStatus("DRAFT");
            task.setCurrentChapterNo(bo.getStartChapterNo() == null ? 0 : bo.getStartChapterNo() - 1);
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!List.of("DRAFT", "READY", PAUSED).contains(task.getStatus())) {
            throw new ServiceException("当前任务状态不能启动");
        }
        return createRun(task);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resumeTask(Long taskId) {
        ReaderSourceTask task = requireTask(taskId);
        if (!PAUSED.equals(task.getStatus())) {
            throw new ServiceException("当前任务不是已暂停状态");
        }
        return createRun(task);
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

    private Long createRun(ReaderSourceTask task) {
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
        taskRunMapper.insert(run);
        task.setStatus(RUNNING);
        task.setLastRunAt(run.getStartedAt());
        taskMapper.updateById(task);
        return run.getId();
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

    private ReaderSourceTaskRunAdminVo toRunVo(ReaderSourceTaskRun run) {
        ReaderSourceTaskRunAdminVo vo = new ReaderSourceTaskRunAdminVo();
        vo.setId(run.getId());
        vo.setTaskId(run.getTaskId());
        vo.setExecutorType(run.getExecutorType());
        vo.setStatus(run.getStatus());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        vo.setHeartbeatAt(run.getHeartbeatAt());
        vo.setRequestCount(run.getRequestCount());
        vo.setSuccessCount(run.getSuccessCount());
        vo.setSkippedCount(run.getSkippedCount());
        vo.setFailureCount(run.getFailureCount());
        vo.setTooManyRequestsCount(run.getTooManyRequestsCount());
        vo.setCircuitOpen(run.getCircuitOpen());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setResultSummary(run.getResultSummary());
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
