package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceDiscoveryBlacklist;
import org.dromara.reader.domain.ReaderSourceDiscoveryCandidate;
import org.dromara.reader.domain.ReaderSourceDiscoveryProvider;
import org.dromara.reader.domain.ReaderSourceDiscoveryRun;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryBlacklistBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryCandidateQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryProviderBo;
import org.dromara.reader.mapper.ReaderSourceDiscoveryBlacklistMapper;
import org.dromara.reader.mapper.ReaderSourceDiscoveryCandidateMapper;
import org.dromara.reader.mapper.ReaderSourceDiscoveryProviderMapper;
import org.dromara.reader.mapper.ReaderSourceDiscoveryRunMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.service.IReaderSourceDiscoveryService;
import org.dromara.reader.service.cache.ReaderSourceRedisCoordinator;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合规的书源地址发现服务。
 *
 * <p>该服务只读取管理员配置的公开索引或授权 Feed，且所有候选请求都在黑名单和公网地址
 * 校验之后执行。发现结果默认停留在候选审核区，不会自动创建可运行采集任务。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReaderSourceDiscoveryServiceImpl implements IReaderSourceDiscoveryService {

    private static final String ENABLED = "1";
    private static final int MAX_FEED_BYTES = 512 * 1024;
    private static final int MAX_CHECK_BYTES = 64 * 1024;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private final ReaderSourceDiscoveryProviderMapper providerMapper;
    private final ReaderSourceDiscoveryBlacklistMapper blacklistMapper;
    private final ReaderSourceDiscoveryCandidateMapper candidateMapper;
    private final ReaderSourceDiscoveryRunMapper runMapper;
    private final ReaderSourceSiteMapper siteMapper;
    private final ReaderSourceRedisCoordinator redisCoordinator;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Override
    public PageResult<ReaderSourceDiscoveryProvider> queryProviderPage(String providerName, String status, PageQuery pageQuery) {
        Page<ReaderSourceDiscoveryProvider> page = providerMapper.selectPage(pageQuery.build(),
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryProvider>lambdaQuery()
                .like(StringUtils.isNotBlank(providerName), ReaderSourceDiscoveryProvider::getProviderName, providerName)
                .eq(StringUtils.isNotBlank(status), ReaderSourceDiscoveryProvider::getStatus, status)
                .orderByDesc(ReaderSourceDiscoveryProvider::getUpdateTime)
                .orderByDesc(ReaderSourceDiscoveryProvider::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveProvider(ReaderSourceDiscoveryProviderBo bo) {
        validateProvider(bo);
        URI uri = ReaderSourceDiscoveryGuard.validatePublicHttpUri(bo.getProviderUrl(), "发现源地址");
        ensureNotBlacklisted(uri, "发现源地址");
        ReaderSourceDiscoveryProvider provider = bo.getId() == null ? new ReaderSourceDiscoveryProvider() : requireProvider(bo.getId());
        provider.setProviderName(bo.getProviderName().trim());
        provider.setProviderUrl(ReaderSourceDiscoveryGuard.normalizeCandidateUrl(uri));
        provider.setProviderType(normalizeProviderType(bo.getProviderType()));
        provider.setAuthorizationNote(trimToLength(bo.getAuthorizationNote(), 1000));
        provider.setPollIntervalSeconds(bo.getPollIntervalSeconds());
        provider.setRequestIntervalMs(bo.getRequestIntervalMs());
        provider.setMaxCandidates(bo.getMaxCandidates());
        if (provider.getId() == null) {
            provider.setStatus("0");
            provider.setLastRunStatus("NEVER");
            providerMapper.insert(provider);
        } else {
            provider.setStatus("0");
            provider.setNextRunAt(null);
            providerMapper.updateById(provider);
        }
        return provider.getId();
    }

    @Override
    public void updateProviderStatus(Long providerId, boolean enabled) {
        ReaderSourceDiscoveryProvider provider = requireProvider(providerId);
        if (enabled) {
            ReaderSourceDiscoveryGuard.validatePublicHttpUri(provider.getProviderUrl(), "发现源地址");
            ensureNotBlacklisted(URI.create(provider.getProviderUrl()), "发现源地址");
        }
        provider.setStatus(enabled ? ENABLED : "0");
        if (enabled && provider.getNextRunAt() == null) {
            provider.setNextRunAt(LocalDateTime.now());
        }
        providerMapper.updateById(provider);
    }

    @Override
    public Long runProvider(Long providerId) {
        ReaderSourceDiscoveryProvider provider = requireProvider(providerId);
        if (!ENABLED.equals(provider.getStatus())) {
            throw new ServiceException("发现源已停用，请先启用后再运行");
        }
        RLock lock = redisCoordinator.discoveryProviderLock(providerId);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!locked) {
                throw new ServiceException("该发现源正在运行，请稍后再试");
            }
            return executeProvider(provider);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("发现任务被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public PageResult<ReaderSourceDiscoveryBlacklist> queryBlacklistPage(String matcherType, String status, PageQuery pageQuery) {
        Page<ReaderSourceDiscoveryBlacklist> page = blacklistMapper.selectPage(pageQuery.build(),
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryBlacklist>lambdaQuery()
                .eq(StringUtils.isNotBlank(matcherType), ReaderSourceDiscoveryBlacklist::getMatcherType, matcherType)
                .eq(StringUtils.isNotBlank(status), ReaderSourceDiscoveryBlacklist::getStatus, status)
                .orderByDesc(ReaderSourceDiscoveryBlacklist::getUpdateTime)
                .orderByDesc(ReaderSourceDiscoveryBlacklist::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveBlacklist(ReaderSourceDiscoveryBlacklistBo bo) {
        if (bo == null) {
            throw new ServiceException("黑名单参数不能为空");
        }
        String type = StringUtils.isBlank(bo.getMatcherType()) ? "HOST" : bo.getMatcherType().trim().toUpperCase(Locale.ROOT);
        String value = normalizeMatcherValue(type, bo.getMatcherValue());
        ReaderSourceDiscoveryGuard.validateMatcher(type, value);
        if ("URL".equals(type)) {
            value = ReaderSourceDiscoveryGuard.normalizeCandidateUrl(
                ReaderSourceDiscoveryGuard.validatePublicHttpUri(value, "黑名单地址"));
        }
        if (StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("黑名单原因不能为空");
        }
        ReaderSourceDiscoveryBlacklist rule = bo.getId() == null ? new ReaderSourceDiscoveryBlacklist() : requireBlacklist(bo.getId());
        rule.setMatcherType(type);
        rule.setMatcherValue(trimToLength(value, 1000));
        rule.setReason(trimToLength(bo.getReason(), 500));
        rule.setSource(trimToLength(bo.getSource(), 128));
        if (rule.getId() == null) {
            rule.setStatus(ENABLED);
            blacklistMapper.insert(rule);
        } else {
            blacklistMapper.updateById(rule);
        }
        return rule.getId();
    }

    @Override
    public void deleteBlacklist(Long blacklistId) {
        requireBlacklist(blacklistId);
        blacklistMapper.deleteById(blacklistId);
    }

    @Override
    public void updateBlacklistStatus(Long blacklistId, boolean enabled) {
        ReaderSourceDiscoveryBlacklist rule = requireBlacklist(blacklistId);
        rule.setStatus(enabled ? ENABLED : "0");
        blacklistMapper.updateById(rule);
    }

    @Override
    public PageResult<ReaderSourceDiscoveryCandidate> queryCandidatePage(ReaderSourceDiscoveryCandidateQueryBo bo, PageQuery pageQuery) {
        Page<ReaderSourceDiscoveryCandidate> page = candidateMapper.selectPage(pageQuery.build(),
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryCandidate>lambdaQuery()
                .eq(bo != null && StringUtils.isNotBlank(bo.getDiscoveryStatus()), ReaderSourceDiscoveryCandidate::getDiscoveryStatus,
                    bo == null ? null : bo.getDiscoveryStatus())
                .eq(bo != null && StringUtils.isNotBlank(bo.getCandidateHost()), ReaderSourceDiscoveryCandidate::getCandidateHost,
                    bo == null ? null : bo.getCandidateHost())
                .and(bo != null && StringUtils.isNotBlank(bo.getKeyword()), wrapper -> wrapper
                    .like(ReaderSourceDiscoveryCandidate::getCandidateUrl, bo.getKeyword())
                    .or().like(ReaderSourceDiscoveryCandidate::getCandidateName, bo.getKeyword()))
                .orderByDesc(ReaderSourceDiscoveryCandidate::getUpdateTime)
                .orderByDesc(ReaderSourceDiscoveryCandidate::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void checkCandidate(Long candidateId) {
        ReaderSourceDiscoveryCandidate candidate = requireCandidate(candidateId);
        inspectCandidate(candidate, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveCandidate(Long candidateId) {
        ReaderSourceDiscoveryCandidate candidate = requireCandidate(candidateId);
        if (!"NEEDS_REVIEW".equals(candidate.getDiscoveryStatus())
            || !"ALLOWED".equals(candidate.getRobotsStatus())
            || !"AVAILABLE".equals(candidate.getAvailabilityStatus())) {
            throw new ServiceException("候选地址必须检查通过后才能审核通过");
        }
        URI uri = ReaderSourceDiscoveryGuard.validatePublicHttpUri(candidate.getCandidateUrl(), "候选地址");
        ensureNotBlacklisted(uri, "候选地址");
        String host = ReaderSourceDiscoveryGuard.normalizeHost(uri.getHost());
        ReaderSourceSite site = siteMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceSite>lambdaQuery()
            .eq(ReaderSourceSite::getAllowedHost, host).last("LIMIT 1"));
        if (site == null) {
            site = new ReaderSourceSite();
            site.setSiteName(StringUtils.isBlank(candidate.getCandidateName()) ? host : candidate.getCandidateName());
            site.setBaseUrl(uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + "/");
            site.setAllowedHost(host);
            site.setComplianceStatus("UNCONFIRMED");
            site.setStatus("0");
            site.setAuthorizationNote("由发现中心审核通过，仍需管理员完成站点授权确认和解析规则配置");
            siteMapper.insert(site);
        }
        candidate.setDiscoveryStatus("APPROVED");
        candidate.setReviewedBy(currentOperatorId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidate.setSiteId(site.getId());
        candidateMapper.updateById(candidate);
    }

    @Override
    public void rejectCandidate(Long candidateId, String reason) {
        ReaderSourceDiscoveryCandidate candidate = requireCandidate(candidateId);
        candidate.setDiscoveryStatus("REJECTED");
        candidate.setReviewedBy(currentOperatorId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidate.setCheckMessage(trimToLength(StringUtils.isBlank(reason) ? "管理员拒绝候选地址" : reason, 1000));
        candidateMapper.updateById(candidate);
    }

    @Override
    public PageResult<ReaderSourceDiscoveryRun> queryRunPage(Long providerId, PageQuery pageQuery) {
        Page<ReaderSourceDiscoveryRun> page = runMapper.selectPage(pageQuery.build(),
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryRun>lambdaQuery()
                .eq(providerId != null, ReaderSourceDiscoveryRun::getProviderId, providerId)
                .orderByDesc(ReaderSourceDiscoveryRun::getStartedAt)
                .orderByDesc(ReaderSourceDiscoveryRun::getId));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public void runDueProviders() {
        LocalDateTime now = LocalDateTime.now();
        List<ReaderSourceDiscoveryProvider> providers = providerMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryProvider>lambdaQuery()
                .eq(ReaderSourceDiscoveryProvider::getStatus, ENABLED)
                .and(wrapper -> wrapper.isNull(ReaderSourceDiscoveryProvider::getNextRunAt)
                    .or().le(ReaderSourceDiscoveryProvider::getNextRunAt, now))
                .orderByAsc(ReaderSourceDiscoveryProvider::getNextRunAt)
                .last("LIMIT 10"));
        for (ReaderSourceDiscoveryProvider provider : providers) {
            try {
                runProvider(provider.getId());
            } catch (Exception ex) {
                log.warn("书源发现源运行失败，providerId={}, message={}", provider.getId(), ex.getMessage());
            }
        }
    }

    private Long executeProvider(ReaderSourceDiscoveryProvider provider) {
        ReaderSourceDiscoveryRun run = new ReaderSourceDiscoveryRun();
        run.setProviderId(provider.getId());
        run.setRunToken(UUID.randomUUID().toString());
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setCandidateCount(0);
        run.setBlockedCount(0);
        run.setRobotsDeniedCount(0);
        run.setAvailableCount(0);
        run.setFailedCount(0);
        runMapper.insert(run);
        provider.setLastRunAt(run.getStartedAt());
        provider.setNextRunAt(run.getStartedAt().plusSeconds(provider.getPollIntervalSeconds()));
        provider.setLastRunStatus("RUNNING");
        provider.setLastError(null);
        providerMapper.updateById(provider);
        try {
            URI providerUri = ReaderSourceDiscoveryGuard.validatePublicHttpUri(provider.getProviderUrl(), "发现源地址");
            ensureNotBlacklisted(providerUri, "发现源地址");
            RobotsResult providerRobots = checkRobots(providerUri);
            if (!providerRobots.allowed()) {
                throw new ServiceException("发现源 robots.txt 不允许访问或无法检查");
            }
            FetchResult feed = fetch(providerUri, MAX_FEED_BYTES);
            if (feed.statusCode() < 200 || feed.statusCode() >= 300) {
                throw new ServiceException("发现源返回 HTTP " + feed.statusCode());
            }
            List<String> urls = extractCandidateUrls(feed.body(), provider.getProviderType(), provider.getMaxCandidates());
            for (String url : urls) {
                run.setCandidateCount(run.getCandidateCount() + 1);
                ReaderSourceDiscoveryCandidate candidate = candidateMapper.selectOne(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryCandidate>lambdaQuery()
                        .eq(ReaderSourceDiscoveryCandidate::getCandidateUrl, url).last("LIMIT 1"));
                if (candidate != null) {
                    if ("BLOCKED".equals(candidate.getDiscoveryStatus())) run.setBlockedCount(run.getBlockedCount() + 1);
                    continue;
                }
                candidate = newCandidate(provider, url);
                candidateMapper.insert(candidate);
                inspectCandidate(candidate, provider.getRequestIntervalMs());
                if ("BLOCKED".equals(candidate.getDiscoveryStatus())) run.setBlockedCount(run.getBlockedCount() + 1);
                else if ("ALLOWED".equals(candidate.getRobotsStatus()) && "AVAILABLE".equals(candidate.getAvailabilityStatus())) {
                    run.setAvailableCount(run.getAvailableCount() + 1);
                } else if ("DISALLOWED".equals(candidate.getRobotsStatus())) {
                    run.setRobotsDeniedCount(run.getRobotsDeniedCount() + 1);
                } else {
                    run.setFailedCount(run.getFailedCount() + 1);
                }
            }
            run.setStatus("COMPLETED");
            provider.setLastRunStatus("COMPLETED");
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(trimToLength(ex.getMessage(), 1000));
            provider.setLastRunStatus("FAILED");
            provider.setLastError(trimToLength(ex.getMessage(), 1000));
        } finally {
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            providerMapper.updateById(provider);
        }
        return run.getId();
    }

    private ReaderSourceDiscoveryCandidate newCandidate(ReaderSourceDiscoveryProvider provider, String url) {
        URI uri = URI.create(url);
        ReaderSourceDiscoveryCandidate candidate = new ReaderSourceDiscoveryCandidate();
        candidate.setProviderId(provider.getId());
        candidate.setCandidateUrl(url);
        candidate.setCandidateHost(ReaderSourceDiscoveryGuard.normalizeHost(uri.getHost()));
        candidate.setCandidateName(ReaderSourceDiscoveryGuard.normalizeHost(uri.getHost()));
        candidate.setDiscoveryStatus("NEEDS_REVIEW");
        candidate.setBlacklistStatus("NOT_MATCHED");
        candidate.setRobotsStatus("NOT_CHECKED");
        candidate.setAvailabilityStatus("NOT_CHECKED");
        return candidate;
    }

    private void inspectCandidate(ReaderSourceDiscoveryCandidate candidate, int intervalMs) {
        URI uri = ReaderSourceDiscoveryGuard.validatePublicHttpUri(candidate.getCandidateUrl(), "候选地址");
        ReaderSourceDiscoveryBlacklist matched = findBlacklist(uri);
        if (matched != null) {
            candidate.setBlacklistStatus("MATCHED");
            candidate.setDiscoveryStatus("BLOCKED");
            candidate.setRobotsStatus("NOT_CHECKED");
            candidate.setAvailabilityStatus("NOT_CHECKED");
            candidate.setCheckMessage(trimToLength("命中黑名单：" + matched.getReason(), 1000));
            candidate.setLastCheckedAt(LocalDateTime.now());
            candidateMapper.updateById(candidate);
            return;
        }
        candidate.setBlacklistStatus("NOT_MATCHED");
        RobotsResult robots = checkRobots(uri);
        candidate.setRobotsStatus(robots.status());
        if (!robots.allowed()) {
            candidate.setAvailabilityStatus("UNAVAILABLE");
            candidate.setDiscoveryStatus("CHECK_FAILED");
            candidate.setCheckMessage(trimToLength(robots.message(), 1000));
            candidate.setLastCheckedAt(LocalDateTime.now());
            candidateMapper.updateById(candidate);
            return;
        }
        sleepQuietly(intervalMs);
        FetchResult response;
        try {
            response = fetch(uri, MAX_CHECK_BYTES);
        } catch (Exception ex) {
            candidate.setAvailabilityStatus("UNAVAILABLE");
            candidate.setDiscoveryStatus("CHECK_FAILED");
            candidate.setCheckMessage(trimToLength(ex.getMessage(), 1000));
            candidate.setLastCheckedAt(LocalDateTime.now());
            candidateMapper.updateById(candidate);
            return;
        }
        candidate.setHttpStatus(response.statusCode());
        candidate.setAvailabilityStatus(response.statusCode() >= 200 && response.statusCode() < 300 ? "AVAILABLE" : "UNAVAILABLE");
        candidate.setDiscoveryStatus("AVAILABLE".equals(candidate.getAvailabilityStatus()) ? "NEEDS_REVIEW" : "CHECK_FAILED");
        candidate.setCheckMessage(response.statusCode() >= 200 && response.statusCode() < 300
            ? "robots.txt 允许访问，基础请求成功，等待人工审核"
            : "基础请求返回 HTTP " + response.statusCode());
        candidate.setLastCheckedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
    }

    private RobotsResult checkRobots(URI siteUri) {
        URI robotsUri = URI.create(siteUri.getScheme().toLowerCase(Locale.ROOT) + "://"
            + ReaderSourceDiscoveryGuard.normalizeHost(siteUri.getHost()) + "/robots.txt");
        try {
            FetchResult response = fetch(robotsUri, 64 * 1024);
            if (response.statusCode() == 404) return RobotsResult.allowed("robots.txt 不存在，未发现禁止规则");
            if (response.statusCode() != 200) return RobotsResult.unavailable("robots.txt 返回 HTTP " + response.statusCode());
            return parseRobots(response.body(), StringUtils.isBlank(siteUri.getPath()) ? "/" : siteUri.getPath());
        } catch (Exception ex) {
            return RobotsResult.unavailable("robots.txt 检查失败：" + ex.getMessage());
        }
    }

    private RobotsResult parseRobots(String body, String path) {
        List<String> disallow = new ArrayList<>();
        List<String> allow = new ArrayList<>();
        boolean applies = false;
        boolean hasAgent = false;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isEmpty()) {
                applies = false;
                hasAgent = false;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("user-agent".equals(key)) {
                hasAgent = true;
                applies = "*".equals(value) || "reader-source-discovery".equalsIgnoreCase(value);
            } else if (hasAgent && applies && "disallow".equals(key) && !value.isEmpty()) {
                disallow.add(value.replace("$", ""));
            } else if (hasAgent && applies && "allow".equals(key) && !value.isEmpty()) {
                allow.add(value.replace("$", ""));
            }
        }
        int deniedLength = longestPrefix(path, disallow);
        int allowedLength = longestPrefix(path, allow);
        return allowedLength >= deniedLength && deniedLength > 0
            ? RobotsResult.denied("robots.txt 禁止发现服务访问站点根路径")
            : RobotsResult.allowed("robots.txt 允许访问");
    }

    private int longestPrefix(String path, List<String> rules) {
        return rules.stream().filter(path::startsWith).mapToInt(String::length).max().orElse(0);
    }

    private FetchResult fetch(URI uri, int maxBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "text/plain, application/json, application/rss+xml, application/xml, text/html;q=0.8")
            .header("User-Agent", "ReaderSourceDiscovery/1.0 (+configured-public-feed)")
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            byte[] body = readLimited(input, maxBytes);
            return new FetchResult(response.statusCode(), new String(body, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("响应内容超过安全限制");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private List<String> extractCandidateUrls(String body, String providerType, int maxCandidates) {
        String payload = body.replace("\\/", "/");
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(payload);
        while (matcher.find() && urls.size() < maxCandidates) {
            String raw = matcher.group().replaceAll("[.,;:)\\]}]+$", "");
            try {
                URI uri = ReaderSourceDiscoveryGuard.validatePublicHttpUri(raw, "候选地址");
                urls.add(ReaderSourceDiscoveryGuard.normalizeCandidateUrl(uri));
            } catch (ServiceException ignored) {
                // Feed 中的无效、内网或携带凭据地址直接丢弃，不发起外部请求。
            }
        }
        return new ArrayList<>(urls);
    }

    private void ensureNotBlacklisted(URI uri, String field) {
        ReaderSourceDiscoveryBlacklist matched = findBlacklist(uri);
        if (matched != null) {
            throw new ServiceException(field + "命中黑名单，系统不会访问：" + matched.getReason());
        }
    }

    private ReaderSourceDiscoveryBlacklist findBlacklist(URI uri) {
        return ReaderSourceDiscoveryGuard.findBlacklist(uri, blacklistMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReaderSourceDiscoveryBlacklist>lambdaQuery()
                .eq(ReaderSourceDiscoveryBlacklist::getStatus, ENABLED)));
    }

    private void validateProvider(ReaderSourceDiscoveryProviderBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getProviderName()) || StringUtils.isBlank(bo.getProviderUrl())
            || StringUtils.isBlank(bo.getAuthorizationNote())) {
            throw new ServiceException("发现源名称、地址和授权说明不能为空");
        }
        if (bo.getProviderName().trim().length() > 128) throw new ServiceException("发现源名称不能超过 128 个字符");
        if (bo.getPollIntervalSeconds() == null) bo.setPollIntervalSeconds(3600);
        if (bo.getRequestIntervalMs() == null) bo.setRequestIntervalMs(3000);
        if (bo.getMaxCandidates() == null) bo.setMaxCandidates(20);
        requireRange(bo.getPollIntervalSeconds(), 900, 7 * 24 * 3600, "轮询间隔");
        requireRange(bo.getRequestIntervalMs(), 1000, 600_000, "检查间隔");
        requireRange(bo.getMaxCandidates(), 1, 100, "单次候选数量");
        normalizeProviderType(bo.getProviderType());
    }

    private String normalizeProviderType(String type) {
        String normalized = StringUtils.isBlank(type) ? "TEXT" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TEXT", "JSON", "RSS").contains(normalized)) throw new ServiceException("发现源类型只能是 TEXT、JSON 或 RSS");
        return normalized;
    }

    private String normalizeMatcherValue(String type, String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if ("HOST".equals(type) || "SUFFIX".equals(type)) {
            return normalized.toLowerCase(Locale.ROOT).replaceFirst("^\\*\\.", "").replaceAll("\\.$", "");
        }
        return normalized;
    }

    private void sleepQuietly(int intervalMs) {
        if (intervalMs <= 0) return;
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("发现检查被中断");
        }
    }

    private ReaderSourceDiscoveryProvider requireProvider(Long id) {
        ReaderSourceDiscoveryProvider provider = id == null ? null : providerMapper.selectById(id);
        if (provider == null) throw new ServiceException("发现源不存在");
        return provider;
    }

    private ReaderSourceDiscoveryBlacklist requireBlacklist(Long id) {
        ReaderSourceDiscoveryBlacklist rule = id == null ? null : blacklistMapper.selectById(id);
        if (rule == null) throw new ServiceException("黑名单规则不存在");
        return rule;
    }

    private ReaderSourceDiscoveryCandidate requireCandidate(Long id) {
        ReaderSourceDiscoveryCandidate candidate = id == null ? null : candidateMapper.selectById(id);
        if (candidate == null) throw new ServiceException("候选地址不存在");
        return candidate;
    }

    private String trimToLength(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private void requireRange(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) throw new ServiceException(field + "必须在 " + min + " 到 " + max + " 之间");
    }

    private Long currentOperatorId() {
        try {
            return org.dromara.common.satoken.utils.LoginHelper.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record FetchResult(int statusCode, String body) {
    }

    private record RobotsResult(String status, boolean allowed, String message) {
        static RobotsResult allowed(String message) { return new RobotsResult("ALLOWED", true, message); }
        static RobotsResult denied(String message) { return new RobotsResult("DISALLOWED", false, message); }
        static RobotsResult unavailable(String message) { return new RobotsResult("UNAVAILABLE", false, message); }
    }
}
