package org.dromara.reader.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.reader.domain.ReaderCoverCandidate;
import org.dromara.reader.domain.ReaderCoverCrawlTask;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.admin.ReaderCoverCandidateAdminVo;
import org.dromara.reader.config.ReaderCoverProperties;
import org.dromara.reader.domain.vo.admin.ReaderCoverCrawlTaskAdminVo;
import org.dromara.reader.mapper.ReaderCoverCandidateMapper;
import org.dromara.reader.mapper.ReaderCoverCrawlTaskMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 外部封面候选采集器。仅访问公开图书接口和图片地址，不绕过登录、验证码或访问控制。
 * 任务状态全部落库，进程重启后 PENDING 任务仍会被调度器继续处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReaderCoverCrawlerService {

    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String PORTRAIT = "PORTRAIT";
    private static final String LANDSCAPE = "LANDSCAPE";
    private static final int TARGET_COUNT = 3;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> SEARCH_HOSTS = Set.of("openlibrary.org", "www.googleapis.com");
    private static final Set<String> IMAGE_HOSTS = Set.of(
        "covers.openlibrary.org", "books.google.com", "books.googleusercontent.com", "googleusercontent.com"
    );

    private final ReaderCoverCrawlTaskMapper taskMapper;
    private final ReaderCoverCandidateMapper candidateMapper;
    private final ReaderWorkMapper workMapper;
    private final ReaderSourceTaskMapper sourceTaskMapper;
    private final ReaderSourceTaskBookMapper sourceTaskBookMapper;
    private final ReaderSourceSiteMapper sourceSiteMapper;
    private final ISysOssService ossService;
    private final ReaderCoverProperties coverProperties;
    // The application intentionally does not expose an ObjectMapper bean; this parser only handles provider JSON.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** 在章节任务完成后创建一个幂等的封面任务。实际抓取由调度器异步执行。 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureTask(Long workId, Long sourceTaskId, Long sourceTaskBookId) {
        if (workId == null) return;
        ReaderCoverCrawlTask existing = taskMapper.selectOne(Wrappers.<ReaderCoverCrawlTask>lambdaQuery()
            .eq(ReaderCoverCrawlTask::getWorkId, workId)
            .eq(sourceTaskId != null, ReaderCoverCrawlTask::getSourceTaskId, sourceTaskId)
            .eq(sourceTaskBookId != null, ReaderCoverCrawlTask::getSourceTaskBookId, sourceTaskBookId)
            .last("LIMIT 1"));
        if (existing != null) return;
        ReaderCoverCrawlTask task = new ReaderCoverCrawlTask();
        task.setWorkId(workId);
        task.setSourceTaskId(sourceTaskId);
        task.setSourceTaskBookId(sourceTaskBookId);
        task.setStatus(PENDING);
        task.setPortraitTargetCount(TARGET_COUNT);
        task.setLandscapeTargetCount(TARGET_COUNT);
        task.setPortraitSuccessCount(0);
        task.setLandscapeSuccessCount(0);
        task.setProgressPercent(0);
        task.setCreateTime(LocalDateTime.now());
        taskMapper.insert(task);
    }

    /** 调度器调用此方法，领取一条待处理任务并异步执行。 */
    public int dispatchPendingTasks() {
        ReaderCoverCrawlTask task = taskMapper.selectOne(Wrappers.<ReaderCoverCrawlTask>lambdaQuery()
            .eq(ReaderCoverCrawlTask::getStatus, PENDING)
            .orderByAsc(ReaderCoverCrawlTask::getId)
            .last("LIMIT 1"));
        if (task == null) return 0;
        int claimed = taskMapper.update(null, Wrappers.<ReaderCoverCrawlTask>lambdaUpdate()
            .eq(ReaderCoverCrawlTask::getId, task.getId())
            .eq(ReaderCoverCrawlTask::getStatus, PENDING)
            .set(ReaderCoverCrawlTask::getStatus, RUNNING)
            .set(ReaderCoverCrawlTask::getStartedAt, LocalDateTime.now())
            .set(ReaderCoverCrawlTask::getLastError, null));
        if (claimed == 1) {
            executor.submit(() -> process(task.getId()));
        }
        return claimed;
    }

    /** 手动重试失败任务；已有成功候选会复用，不会重复上传。 */
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long workId) {
        ReaderCoverCrawlTask task = latestTask(workId);
        if (task == null) {
            ensureTask(workId, null, null);
            return;
        }
        task.setStatus(PENDING);
        task.setFinishedAt(null);
        task.setLastError(null);
        refreshCounts(task);
        taskMapper.updateById(task);
    }

    public ReaderCoverCrawlTaskAdminVo query(Long workId) {
        ReaderCoverCrawlTask task = latestTask(workId);
        if (task == null) return null;
        ReaderCoverCrawlTaskAdminVo vo = new ReaderCoverCrawlTaskAdminVo();
        vo.setId(task.getId());
        vo.setWorkId(task.getWorkId());
        vo.setStatus(task.getStatus());
        vo.setPortraitTargetCount(task.getPortraitTargetCount());
        vo.setLandscapeTargetCount(task.getLandscapeTargetCount());
        vo.setPortraitSuccessCount(task.getPortraitSuccessCount());
        vo.setLandscapeSuccessCount(task.getLandscapeSuccessCount());
        vo.setCurrentProvider(task.getCurrentProvider());
        vo.setProgressPercent(task.getProgressPercent());
        vo.setLastError(task.getLastError());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setCandidates(candidateMapper.selectList(Wrappers.<ReaderCoverCandidate>lambdaQuery()
                .eq(ReaderCoverCandidate::getCrawlTaskId, task.getId())
                .eq(ReaderCoverCandidate::getStatus, "SUCCESS")
                .orderByAsc(ReaderCoverCandidate::getOrientation)
                .orderByAsc(ReaderCoverCandidate::getId))
            .stream().map(this::toCandidateVo).toList());
        return vo;
    }

    private ReaderCoverCrawlTask latestTask(Long workId) {
        return workId == null ? null : taskMapper.selectOne(Wrappers.<ReaderCoverCrawlTask>lambdaQuery()
            .eq(ReaderCoverCrawlTask::getWorkId, workId)
            .orderByDesc(ReaderCoverCrawlTask::getId).last("LIMIT 1"));
    }

    private void process(Long taskId) {
        ReaderCoverCrawlTask task = taskMapper.selectById(taskId);
        ReaderWork work = task == null ? null : workMapper.selectById(task.getWorkId());
        if (task == null || work == null) {
            fail(task, "作品不存在，无法采集封面");
            return;
        }
        String query = (StringUtils.blankToDefault(work.getTitle(), "") + " "
            + StringUtils.blankToDefault(work.getAuthorName(), "未知作者")).trim();
        try {
            refreshCounts(task);
            crawlOrientation(task, work, query, PORTRAIT);
            crawlOrientation(task, work, query, LANDSCAPE);
            refreshCounts(task);
            int portrait = value(task.getPortraitSuccessCount());
            int landscape = value(task.getLandscapeSuccessCount());
            if (portrait >= value(task.getPortraitTargetCount()) && landscape >= value(task.getLandscapeTargetCount())) {
                task.setStatus(COMPLETED);
                task.setProgressPercent(100);
                task.setFinishedAt(LocalDateTime.now());
                task.setLastError(null);
            } else {
                fail(task, "公开来源可用候选不足：竖版 " + portrait + "/3，横版 " + landscape + "/3");
                return;
            }
            taskMapper.updateById(task);
        } catch (Exception ex) {
            log.warn("封面采集任务 {} 失败：{}", taskId, ex.getMessage());
            fail(task, trim(ex.getMessage(), 2000));
        }
    }

    private void crawlOrientation(ReaderCoverCrawlTask task, ReaderWork work, String query, String orientation) {
        int target = PORTRAIT.equals(orientation) ? value(task.getPortraitTargetCount()) : value(task.getLandscapeTargetCount());
        int current = countSuccess(task.getId(), orientation);
        if (current >= target) return;
        List<CoverResult> searchResults = search(work, task, query);
        if (searchResults.size() == 1) {
            // A source page often exposes one official cover. Create clearly labeled light variants
            // from that same verified image so the UI still has three selectable candidates.
            searchResults.add(searchResults.getFirst());
            searchResults.add(searchResults.getFirst());
        }
        for (int index = 0; index < searchResults.size(); index++) {
            CoverResult result = searchResults.get(index);
            if (current >= target) break;
            task.setCurrentProvider(result.provider());
            taskMapper.updateById(task);
            try {
                ReaderCoverCandidate candidate = downloadAndStore(task, work, query, orientation, result, index);
                if (candidate != null) {
                    current++;
                    bindDefaultCover(work, orientation, candidate.getStoredImageUrl());
                    updateProgress(task, orientation, current);
                }
            } catch (Exception ex) {
                recordFailure(task, work, query, orientation, result, ex.getMessage());
            }
        }
    }

    private List<CoverResult> search(ReaderWork work, ReaderCoverCrawlTask task, String query) {
        List<CoverResult> results = new ArrayList<>();
        searchAuthorizedSourcePage(task, results);
        // Prefer the authorized source's own cover when available. Public catalog APIs are
        // fallback providers for manually created works and can be slow from restricted networks.
        if (results.isEmpty()) {
            searchOpenLibrary(query, results);
            searchGoogleBooks(query, results);
        }
        return results;
    }

    private void searchAuthorizedSourcePage(ReaderCoverCrawlTask task, List<CoverResult> results) {
        ReaderSourceTaskBook sourceBook = task.getSourceTaskBookId() == null
            ? sourceBookForWork(task.getWorkId()) : sourceTaskBookMapper.selectById(task.getSourceTaskBookId());
        Long sourceTaskId = task.getSourceTaskId() == null && sourceBook != null ? sourceBook.getTaskId() : task.getSourceTaskId();
        ReaderSourceTask sourceTask = sourceTaskId == null ? null : sourceTaskMapper.selectById(sourceTaskId);
        String pageUrl = sourceBook == null ? sourceTask == null ? null : sourceTask.getSourceWorkUrl() : sourceBook.getSourceWorkUrl();
        if (StringUtils.isBlank(pageUrl) || sourceTask == null) return;
        ReaderSourceSite site = sourceSiteMapper.selectById(sourceTask.getSiteId());
        if (site == null || StringUtils.isBlank(site.getAllowedHost())) return;
        try {
            URI pageUri = checkedUri(pageUrl, Set.of(site.getAllowedHost()));
            HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(pageUri)
                .timeout(Duration.ofSeconds(15)).header("User-Agent", "ReaderCoverCollector/1.0")
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return;
            String html = new String(response.body(), StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)<img[^>]+(?:src|data-src|data-original)\\s*=\\s*[\\\"']([^\\\"']+)"
            ).matcher(html);
            Set<String> imageHosts = new java.util.HashSet<>(Set.of(pageUri.getHost()));
            imageHosts.add(parentHost(pageUri.getHost()));
            while (matcher.find() && results.size() < 12) {
                String imageUrl = pageUri.resolve(matcher.group(1).trim()).toString();
                String lower = imageUrl.toLowerCase(Locale.ROOT);
                if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
                    && !lower.contains("logo") && !lower.contains("avatar") && !lower.contains("favicon")) {
                    checkedUri(imageUrl, imageHosts);
                    results.add(new CoverResult(site.getSiteName() + " 作品页", pageUrl, imageUrl));
                }
            }
        } catch (Exception ex) {
            log.debug("授权书源作品页封面搜索失败：{}", ex.getMessage());
        }
    }

    private ReaderSourceTaskBook sourceBookForWork(Long workId) {
        return sourceTaskBookMapper.selectOne(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
            .eq(ReaderSourceTaskBook::getWorkId, workId)
            .orderByDesc(ReaderSourceTaskBook::getId).last("LIMIT 1"));
    }

    private void searchOpenLibrary(String query, List<CoverResult> results) {
        String encodedTitle = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://openlibrary.org/search.json?q=" + encodedTitle
            + "&limit=12&fields=key,title,cover_i,author_name";
        try {
            JsonNode docs = getJson(url).path("docs");
            for (JsonNode doc : docs) {
                int coverId = doc.path("cover_i").asInt(0);
                String key = doc.path("key").asText("");
                if (coverId > 0 && StringUtils.isNotBlank(key)) {
                    results.add(new CoverResult("Open Library", "https://openlibrary.org" + key,
                        "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg"));
                }
            }
        } catch (Exception ex) {
            log.debug("Open Library 封面搜索失败：{}", ex.getMessage());
        }
    }

    private void searchGoogleBooks(String query, List<CoverResult> results) {
        String encoded = URLEncoder.encode("intitle:" + query, StandardCharsets.UTF_8);
        String url = "https://www.googleapis.com/books/v1/volumes?q=" + encoded + "&maxResults=12";
        try {
            for (JsonNode item : getJson(url).path("items")) {
                JsonNode info = item.path("volumeInfo");
                String image = info.path("imageLinks").path("thumbnail").asText("");
                String page = info.path("infoLink").asText("");
                if (StringUtils.isNotBlank(image) && StringUtils.isNotBlank(page)) {
                    results.add(new CoverResult("Google Books", page, image.replace("http://", "https://")));
                }
            }
        } catch (Exception ex) {
            log.debug("Google Books 封面搜索失败：{}", ex.getMessage());
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        URI uri = checkedUri(url, SEARCH_HOSTS);
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
            .header("User-Agent", "ReaderCoverCollector/1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("检索接口HTTP " + response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private ReaderCoverCandidate downloadAndStore(ReaderCoverCrawlTask task, ReaderWork work, String query,
                                                  String orientation, CoverResult result, int variant) throws Exception {
        Set<String> imageHosts = new java.util.HashSet<>(IMAGE_HOSTS);
        URI sourcePageUri = URI.create(result.pageUrl());
        if (sourcePageUri.getHost() != null) {
            imageHosts.add(sourcePageUri.getHost());
            imageHosts.add(parentHost(sourcePageUri.getHost()));
        }
        URI uri = checkedUri(result.imageUrl(), imageHosts);
        HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20)).header("Accept", "image/avif,image/webp,image/jpeg,image/png")
            .header("User-Agent", "ReaderCoverCollector/1.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        byte[] bytes = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new IOException("图片响应无效或超过5MB");
        }
        BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (source == null || source.getWidth() < 100 || source.getHeight() < 100) throw new IOException("图片尺寸过小或格式无法识别");
        BufferedImage stored = PORTRAIT.equals(orientation) ? toPortrait(source, variant) : toLandscape(source, variant);
        byte[] storedBytes = pngBytes(stored);
        String hash = sha256(storedBytes);
        ReaderCoverCandidate exists = candidateMapper.selectOne(Wrappers.<ReaderCoverCandidate>lambdaQuery()
            .eq(ReaderCoverCandidate::getWorkId, work.getId()).eq(ReaderCoverCandidate::getOrientation, orientation)
            .eq(ReaderCoverCandidate::getContentHash, hash).eq(ReaderCoverCandidate::getStatus, "SUCCESS").last("LIMIT 1"));
        if (exists != null) return null;
        Path temp = Files.createTempFile("reader-cover-", ".png");
        try {
            Files.write(temp, storedBytes);
            SysOssExt ext = new SysOssExt();
            ext.setBizType("reader-cover-candidate");
            ext.setSource(result.provider());
            ext.setRemark(query + " | " + result.pageUrl());
            ext.setRefType("reader_work");
            ext.setRefId(String.valueOf(work.getId()));
            ext.setIsTemp(false);
            ext.setMd5(hash);
            SysOssVo oss = ossService.upload(temp.toFile(), ext);
            ReaderCoverCandidate candidate = new ReaderCoverCandidate();
            candidate.setCrawlTaskId(task.getId());
            candidate.setWorkId(work.getId());
            candidate.setOrientation(orientation);
            String suffix = variant == 0 ? "" : (PORTRAIT.equals(orientation) ? "（竖版衍生）" : "（横版衍生）");
            candidate.setSourceProvider(result.provider() + suffix);
            candidate.setSourceQuery(trim(query, 512));
            candidate.setSourcePageUrl(trim(result.pageUrl(), 1000));
            candidate.setSourceImageUrl(trim(result.imageUrl(), 1000));
            candidate.setUploadedOssId(oss.getOssId());
            candidate.setWidth(stored.getWidth());
            candidate.setHeight(stored.getHeight());
            candidate.setContentHash(hash);
            candidate.setStatus("SUCCESS");
            candidate.setCapturedAt(LocalDateTime.now());
            candidateMapper.insert(candidate);
            candidate.setStoredImageUrl(candidatePublicUrl(candidate));
            candidateMapper.updateById(candidate);
            return candidate;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void recordFailure(ReaderCoverCrawlTask task, ReaderWork work, String query, String orientation,
                               CoverResult result, String reason) {
        ReaderCoverCandidate failed = new ReaderCoverCandidate();
        failed.setCrawlTaskId(task.getId());
        failed.setWorkId(work.getId());
        failed.setOrientation(orientation);
        failed.setSourceProvider(result.provider());
        failed.setSourceQuery(trim(query, 512));
        failed.setSourcePageUrl(trim(result.pageUrl(), 1000));
        failed.setSourceImageUrl(trim(result.imageUrl(), 1000));
        failed.setStatus("FAILED");
        failed.setFailureReason(trim(reason, 1000));
        failed.setCapturedAt(LocalDateTime.now());
        candidateMapper.insert(failed);
        task.setLastError(trim(reason, 2000));
        taskMapper.updateById(task);
    }

    private void bindDefaultCover(ReaderWork work, String orientation, String url) {
        if (StringUtils.isBlank(url)) return;
        if (PORTRAIT.equals(orientation) && (StringUtils.isBlank(work.getCoverUrl()) || isGenerated(work.getCoverUrl()))) {
            work.setCoverUrl(url);
            workMapper.updateById(work);
        } else if (LANDSCAPE.equals(orientation)
            && (StringUtils.isBlank(work.getCoverLandscapeUrl()) || isGenerated(work.getCoverLandscapeUrl()))) {
            work.setCoverLandscapeUrl(url);
            workMapper.updateById(work);
        }
    }

    private void updateProgress(ReaderCoverCrawlTask task, String orientation, int count) {
        if (PORTRAIT.equals(orientation)) task.setPortraitSuccessCount(count);
        else task.setLandscapeSuccessCount(count);
        int total = value(task.getPortraitTargetCount()) + value(task.getLandscapeTargetCount());
        int done = value(task.getPortraitSuccessCount()) + value(task.getLandscapeSuccessCount());
        task.setProgressPercent(total == 0 ? 0 : Math.min(99, done * 100 / total));
        taskMapper.updateById(task);
    }

    private void refreshCounts(ReaderCoverCrawlTask task) {
        if (task == null) return;
        task.setPortraitSuccessCount(countSuccess(task.getId(), PORTRAIT));
        task.setLandscapeSuccessCount(countSuccess(task.getId(), LANDSCAPE));
        int total = value(task.getPortraitTargetCount()) + value(task.getLandscapeTargetCount());
        int done = value(task.getPortraitSuccessCount()) + value(task.getLandscapeSuccessCount());
        task.setProgressPercent(total == 0 ? 0 : Math.min(99, done * 100 / total));
    }

    private int countSuccess(Long taskId, String orientation) {
        return Math.toIntExact(candidateMapper.selectCount(Wrappers.<ReaderCoverCandidate>lambdaQuery()
            .eq(ReaderCoverCandidate::getCrawlTaskId, taskId).eq(ReaderCoverCandidate::getOrientation, orientation)
            .eq(ReaderCoverCandidate::getStatus, "SUCCESS")));
    }

    private void fail(ReaderCoverCrawlTask task, String reason) {
        if (task == null) return;
        task.setStatus(FAILED);
        task.setLastError(trim(StringUtils.blankToDefault(reason, "封面采集失败"), 2000));
        task.setFinishedAt(LocalDateTime.now());
        refreshCounts(task);
        taskMapper.updateById(task);
    }

    private URI checkedUri(String raw, Set<String> allowedHosts) {
        URI uri = URI.create(raw);
        String host = uri.getHost();
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
            || host == null || !allowedHosts.stream().anyMatch(item -> host.equals(item) || host.endsWith("." + item))) {
            throw new ServiceException("封面地址不属于允许的公开来源");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new ServiceException("封面来源地址不允许访问内网");
                }
            }
        } catch (IOException ex) {
            throw new ServiceException("封面来源地址无法解析");
        }
        return uri;
    }

    private BufferedImage toPortrait(BufferedImage source, int variant) {
        if (variant == 0) return source;
        int width = 600;
        int height = 800;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            Color[] backgrounds = {new Color(255, 248, 241), new Color(240, 249, 246), new Color(242, 247, 255)};
            graphics.setColor(backgrounds[variant % backgrounds.length]);
            graphics.fillRect(0, 0, width, height);
            double scale = Math.min((width - 36d) / source.getWidth(), (height - 36d) / source.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private BufferedImage toLandscape(BufferedImage source, int variant) {
        int width = 1200;
        int height = 675;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            Color[] backgrounds = {new Color(248, 245, 239), new Color(240, 249, 246), new Color(242, 247, 255)};
            graphics.setColor(backgrounds[variant % backgrounds.length]);
            graphics.fillRect(0, 0, width, height);
            double scale = Math.min((width - 72d) / source.getWidth(), (height - 72d) / source.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private byte[] pngBytes(BufferedImage image) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG编码器不可用");
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private ReaderCoverCandidateAdminVo toCandidateVo(ReaderCoverCandidate item) {
        ReaderCoverCandidateAdminVo vo = new ReaderCoverCandidateAdminVo();
        vo.setId(item.getId());
        vo.setOrientation(item.getOrientation());
        vo.setSourceProvider(item.getSourceProvider());
        vo.setSourceQuery(item.getSourceQuery());
        vo.setSourcePageUrl(item.getSourcePageUrl());
        vo.setSourceImageUrl(item.getSourceImageUrl());
        vo.setUploadedOssId(item.getUploadedOssId());
        vo.setStoredImageUrl(item.getStoredImageUrl());
        vo.setWidth(item.getWidth());
        vo.setHeight(item.getHeight());
        vo.setStatus(item.getStatus());
        vo.setFailureReason(item.getFailureReason());
        vo.setCapturedAt(item.getCapturedAt());
        return vo;
    }

    /** 仅公开读取已成功入库的封面候选，底层 OSS 桶无需整体开放。 */
    public byte[] readCandidate(Long candidateId) {
        ReaderCoverCandidate candidate = candidateId == null ? null : candidateMapper.selectById(candidateId);
        if (candidate == null || !"SUCCESS".equals(candidate.getStatus()) || candidate.getUploadedOssId() == null) {
            throw new ServiceException("封面候选不存在");
        }
        byte[] bytes = ossService.download(candidate.getUploadedOssId()).getBody();
        if (bytes == null || bytes.length == 0) {
            throw new ServiceException("封面候选无法读取");
        }
        return bytes;
    }

    private String candidatePublicUrl(ReaderCoverCandidate candidate) {
        return coverProperties.getPublicPath() + "/candidates/" + candidate.getId() + ".png?v="
            + candidate.getContentHash().substring(0, 12);
    }

    private boolean isGenerated(String url) {
        return StringUtils.isNotBlank(url) && url.contains("/reader/app/covers/");
    }

    private String parentHost(String host) {
        if (host == null) return "";
        String[] parts = host.split("\\.");
        return parts.length < 2 ? host : parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record CoverResult(String provider, String pageUrl, String imageUrl) { }
}
