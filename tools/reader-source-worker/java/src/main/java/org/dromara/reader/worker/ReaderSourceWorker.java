package org.dromara.reader.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reference Java worker for the reader source protocol. */
public final class ReaderSourceWorker {
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final Pattern CHAPTER_NUMBER = Pattern.compile("(?:第\\s*)?(\\d{1,7})");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final String apiBase;
    private final String secret;
    private final String workerId;
    private final int pollSeconds;
    private final boolean allowPrivateForTest;

    private ReaderSourceWorker() {
        apiBase = env("READER_SOURCE_API_BASE_URL", "http://127.0.0.1:8080").replaceAll("/$", "");
        secret = required("READER_SOURCE_WORKER_SECRET");
        workerId = env("READER_SOURCE_WORKER_ID", "java-worker");
        pollSeconds = Math.max(2, Integer.parseInt(env("READER_SOURCE_POLL_SECONDS", "5")));
        allowPrivateForTest = "true".equalsIgnoreCase(env("READER_SOURCE_ALLOW_PRIVATE_FOR_TEST", "false"));
    }

    public static void main(String[] args) throws Exception {
        new ReaderSourceWorker().runForever();
    }

    private void runForever() throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                JsonNode data = post("/reader/worker/source/runs/claim", Map.of("executorType", "JAVA", "workerId", workerId));
                if (data == null || data.isNull()) {
                    Thread.sleep(pollSeconds * 1000L);
                    continue;
                }
                Task task = mapper.convertValue(data, Task.class);
                System.out.printf("claimed run=%d executor=JAVA%n", task.runId);
                runTask(task);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                System.err.println("worker loop failed: " + ex.getMessage());
                Thread.sleep(pollSeconds * 1000L);
            }
        }
    }

    private void runTask(Task task) throws Exception {
        Thread heartbeat = new Thread(() -> heartbeatLoop(task), "reader-source-heartbeat-" + task.runId);
        heartbeat.setDaemon(true);
        heartbeat.start();
        try {
            runTaskBody(task);
        } catch (Exception ex) {
            reportError(task, ex);
        } finally {
            heartbeat.interrupt();
        }
    }

    private void runTaskBody(Task task) throws Exception {
        Map<String, Object> selectors = mapper.readValue(task.selectorJson, new TypeReference<>() {});
        Map<String, Object> catalogSelector = map(selectors, "catalog");
        Map<String, Object> chapterSelector = map(selectors, "chapter");
        String itemSelector = requiredSelector(catalogSelector, "item");
        String titleSelector = requiredSelector(catalogSelector, "title");
        String contentSelector = requiredSelector(chapterSelector, "content");
        String catalogUrl = expandUrl(value(task.catalogUrlTemplate, task.sourceWorkUrl), task.sourceWorkUrl, "", 0);
        if (catalogUrl.isBlank()) catalogUrl = task.sourceWorkUrl;
        validatePublicSameHost(catalogUrl, task.sourceWorkUrl);
        String catalogHtml = fetchWithRetry(task, catalogUrl);
        List<Chapter> chapters = selectRange(extractCatalog(catalogHtml, itemSelector, titleSelector, catalogUrl), task);
        if (chapters.isEmpty()) throw new IllegalStateException("catalog contains no chapters in the requested range");

        for (int offset = 0; offset < chapters.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, chapters.size());
            List<Map<String, Object>> items = new ArrayList<>();
            for (Chapter chapter : chapters.subList(offset, end)) {
                String chapterUrl = expandUrl(value(task.chapterUrlTemplate, chapter.url), task.sourceWorkUrl, chapter.url, chapter.chapterNo);
                if (chapterUrl.isBlank()) chapterUrl = chapter.url;
                validatePublicSameHost(chapterUrl, task.sourceWorkUrl);
                String chapterHtml = fetchWithRetry(task, chapterUrl);
                items.add(extractChapter(chapterHtml, value(chapterSelector, "title"), contentSelector, chapter, chapterUrl));
                politeDelay(task);
            }
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("requests", items.size() + 1);
            metrics.put("success", items.size());
            post("/reader/worker/source/runs/" + task.runId + "/result", Map.of(
                "runId", task.runId, "runToken", task.runToken, "workerId", workerId, "executorType", "JAVA",
                "batchId", "java-" + task.runId + "-" + System.nanoTime(), "ruleVersion", task.ruleVersion,
                "cursorChapterNo", chapters.get(end - 1).chapterNo, "completed", end == chapters.size(),
                "items", items, "metrics", metrics));
        }
    }

    private void heartbeatLoop(Task task) {
        long interval = Math.max(10, task.claimLeaseSeconds / 3) * 1000L;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(interval);
                post("/reader/worker/source/runs/" + task.runId + "/heartbeat", Map.of(
                    "runId", task.runId, "runToken", task.runToken, "workerId", workerId));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            System.err.println("heartbeat failed run=" + task.runId + ": " + ex.getMessage());
        }
    }

    private void reportError(Task task, Exception exception) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", task.runId);
        payload.put("runToken", task.runToken);
        payload.put("workerId", workerId);
        payload.put("errorType", exception instanceof SourceHttpException ? "HTTP" : "PARSE");
        payload.put("sourceUrl", task.sourceWorkUrl);
        payload.put("message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        if (exception instanceof SourceHttpException httpException) {
            payload.put("httpStatus", httpException.status);
            if (!httpException.retryAfter.isBlank() && httpException.retryAfter.chars().allMatch(Character::isDigit)) {
                payload.put("retryAt", java.time.LocalDateTime.now().plusSeconds(Long.parseLong(httpException.retryAfter)).withNano(0).toString());
            }
        }
        try {
            post("/reader/worker/source/runs/" + task.runId + "/error", payload);
        } catch (Exception reportException) {
            System.err.println("failed to report run=" + task.runId + ": " + reportException.getMessage());
        }
    }

    private String fetchWithRetry(Task task, String target) throws Exception {
        int retries = Math.min(5, Math.max(0, task.maxRetries));
        for (int attempt = 0; attempt <= retries; attempt++) {
            waitForPermit(task);
            try {
                return fetch(target, task);
            } catch (SourceHttpException ex) {
                if (ex.status == 401 || ex.status == 403 || ex.status == 429 || attempt >= retries) throw ex;
                Thread.sleep((1L << attempt) * 1000L);
            }
        }
        throw new IllegalStateException("request retry exhausted");
    }

    private String fetch(String target, Task task) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
            .timeout(Duration.ofMillis(Math.max(1000, task.connectTimeoutMs + task.readTimeoutMs)))
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("User-Agent", "reader-source-worker/1.0 (+authorized-fetch)").GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SourceHttpException(response.statusCode(), response.headers().firstValue("Retry-After").orElse(""),
                "source returned HTTP " + response.statusCode());
        }
        if (response.body().length > MAX_RESPONSE_BYTES) throw new IOException("source response exceeds size limit");
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private void waitForPermit(Task task) throws Exception {
        while (true) {
            JsonNode permit = post("/reader/worker/source/runs/" + task.runId + "/permit", Map.of(
                "runId", task.runId, "runToken", task.runToken, "workerId", workerId));
            if (permit.path("allowed").asBoolean()) return;
            Thread.sleep(Math.min(300_000L, Math.max(100L, permit.path("retryAfterMs").asLong(1000))));
        }
    }

    private JsonNode post(String path, Object payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path)).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json").header("Accept", "application/json")
            .header("X-Reader-Worker-Secret", secret)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("worker API returned HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        int code = root.path("code").asInt(200);
        if (code != 0 && code != 200) throw new IOException(root.path("msg").asText("worker API request failed"));
        return root.get("data");
    }

    private List<Chapter> extractCatalog(String html, String itemSelector, String titleSelector, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        List<Chapter> result = new ArrayList<>();
        int index = 0;
        for (Element node : document.select(itemSelector)) {
            Element anchor = node.select("a[href]").first();
            if (anchor == null && node.is("a[href]")) anchor = node;
            if (anchor == null) continue;
            String url = anchor.absUrl("href");
            if (url.isBlank()) continue;
            Element title = node.select(titleSelector).first();
            String name = clean((title == null ? anchor : title).text());
            result.add(new Chapter(url, chapterNumber(name, ++index), name));
        }
        return result;
    }

    private Map<String, Object> extractChapter(String html, String titleSelector, String contentSelector, Chapter chapter, String url) throws Exception {
        Document document = Jsoup.parse(html, url);
        Element content = document.select(contentSelector).first();
        if (content == null || clean(content.text()).isBlank()) throw new IllegalStateException("chapter content selector matched no element");
        String title = titleSelector == null || titleSelector.isBlank() ? chapter.name : clean(document.select(titleSelector).first() == null ? chapter.name : document.select(titleSelector).first().text());
        String text = clean(content.wholeText());
        return Map.of("sourceChapterId", chapter.url, "sourceUrl", url, "chapterNo", chapter.chapterNo,
            "chapterName", title, "content", text, "contentHash", sha256(text));
    }

    private List<Chapter> selectRange(List<Chapter> chapters, Task task) {
        int start = Math.max(task.cursorChapterNo + 1, task.startChapterNo);
        return chapters.stream().filter(c -> c.chapterNo >= start && (task.endChapterNo == null || c.chapterNo <= task.endChapterNo)).toList();
    }

    private void validatePublicSameHost(String target, String approved) throws Exception {
        URI got = URI.create(target), source = URI.create(approved);
        if (!(got.getScheme().equalsIgnoreCase("http") || got.getScheme().equalsIgnoreCase("https")) || got.getHost() == null || got.getUserInfo() != null) throw new IOException("source URL must be a credential-free HTTP(S) URL");
        if (!got.getHost().equalsIgnoreCase(source.getHost())) throw new IOException("source URL leaves the approved host");
        if (allowPrivateForTest) return;
        for (InetAddress address : InetAddress.getAllByName(got.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) throw new IOException("source URL resolves to a non-public address");
        }
    }

    private static void politeDelay(Task task) throws InterruptedException {
        int min = Math.max(0, task.minDelayMs), max = Math.max(min, task.maxDelayMs);
        if (max > 0) Thread.sleep(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private static String expandUrl(String template, String workUrl, String chapterUrl, int chapterNo) {
        if (template == null || template.isBlank()) return "";
        URI work = URI.create(workUrl);
        String id = work.getPath().replaceAll("/$", "");
        id = id.substring(id.lastIndexOf('/') + 1);
        String chapterId = chapterUrl == null || chapterUrl.isBlank() ? id : URI.create(chapterUrl).getPath().replaceAll("/$", "");
        chapterId = chapterId.substring(chapterId.lastIndexOf('/') + 1);
        String value = template.replace("{url}", workUrl).replace("{workUrl}", workUrl).replace("{id}", chapterId).replace("{chapterId}", chapterId).replace("{chapterNo}", String.valueOf(chapterNo));
        return work.resolve(value).toString();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder("sha256:");
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }

    private static int chapterNumber(String title, int fallback) {
        Matcher matcher = CHAPTER_NUMBER.matcher(title);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static String clean(String value) { return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("[ \\t\\r\\f]+", " ").replaceAll("\\n{3,}", "\\n\\n").trim(); }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
    private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required"); return value; }
    private static String value(Object source, String fallback) { return source == null ? fallback : String.valueOf(source); }
    private static String value(Map<String, Object> source, String key) { return source == null ? "" : value(source.get(key), ""); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Map<String, Object> source, String key) { Object value = source.get(key); return value instanceof Map ? (Map<String, Object>) value : Map.of(); }
    private static String requiredSelector(Map<String, Object> source, String key) { String value = value(source, key); if (value.isBlank()) throw new IllegalArgumentException("required selector missing: " + key); return value; }

    private static final class SourceHttpException extends IOException {
        private final int status;
        private final String retryAfter;
        private SourceHttpException(int status, String retryAfter, String message) { super(message + (retryAfter.isBlank() ? "" : " retryAfter=" + retryAfter)); this.status = status; this.retryAfter = retryAfter; }
    }

    private static final class Chapter {
        private final String url, name;
        private final int chapterNo;
        private Chapter(String url, int chapterNo, String name) { this.url = Objects.requireNonNull(url); this.chapterNo = chapterNo; this.name = name; }
    }

    public static final class Task {
        public long runId, taskId, siteId, ruleId;
        public String runToken, workerId, executorType, sourceWorkUrl, sourceWorkTitle, catalogUrlTemplate, chapterUrlTemplate, selectorJson, honorRetryAfter;
        public int ruleVersion, cursorChapterNo, startChapterNo, maxRetries, connectTimeoutMs, readTimeoutMs, minDelayMs, maxDelayMs, claimLeaseSeconds, concurrencyLimit, requestsPerMinute, dailyRequestLimit, circuitBreakerThreshold;
        public Integer endChapterNo;
    }
}
