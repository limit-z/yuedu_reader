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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reference Java worker for the reader source protocol. */
public final class ReaderSourceWorker {
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final Pattern CHAPTER_NUMBER = Pattern.compile("第\\s*([0-9零〇一二两三四五六七八九十百千万亿]+)\\s*(?:章|节|回|集)");

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
        if ("ALL".equals(task.collectionMode) || "CATEGORY".equals(task.collectionMode)) {
            runBatchTask(task, selectors);
            return;
        }
        String sourceWorkUrl = resolveSourceWorkUrl(task, selectors);
        Map<String, Object> catalogSelector = map(selectors, "catalog");
        Map<String, Object> chapterSelector = map(selectors, "chapter");
        String itemSelector = requiredSelector(catalogSelector, "item");
        String titleSelector = requiredSelector(catalogSelector, "title");
        String contentSelector = requiredSelector(chapterSelector, "content");
        String catalogUrl = expandUrl(value(task.catalogUrlTemplate, sourceWorkUrl), sourceWorkUrl, "", 0);
        if (catalogUrl.isBlank()) catalogUrl = sourceWorkUrl;
        validatePublicSameHost(catalogUrl, sourceWorkUrl);
        String catalogHtml = fetchWithRetry(task, catalogUrl);
        List<Chapter> chapters = selectRange(extractCatalog(catalogHtml, itemSelector, titleSelector, catalogUrl,
            Boolean.parseBoolean(value(catalogSelector, "chapterNoFromUrl"))), task);
        if (chapters.isEmpty()) throw new IllegalStateException("catalog contains no chapters in the requested range");

        for (int offset = 0; offset < chapters.size();) {
            List<Map<String, Object>> items = new ArrayList<>();
            while (offset < chapters.size() && items.size() < BATCH_SIZE) {
                Chapter chapter = chapters.get(offset);
                String chapterUrl = expandUrl(value(task.chapterUrlTemplate, chapter.url), task.sourceWorkUrl, chapter.url, chapter.chapterNo);
                if (chapterUrl.isBlank()) chapterUrl = chapter.url;
                validatePublicSameHost(chapterUrl, task.sourceWorkUrl);
                Map<String, Object> item = fetchChapter(task, chapterSelector, chapter, chapterUrl);
                insertDiscoveredNextChapter(chapters, offset, item, task);
                items.add(item);
                offset++;
                politeDelay(task);
            }
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("requests", items.size() + 1);
            metrics.put("success", items.size());
            post("/reader/worker/source/runs/" + task.runId + "/result", Map.of(
                "runId", task.runId, "runToken", task.runToken, "workerId", workerId, "executorType", "JAVA",
                "batchId", "java-" + task.runId + "-" + System.nanoTime(), "ruleVersion", task.ruleVersion,
                "cursorChapterNo", items.getLast().get("chapterNo"), "completed", offset == chapters.size(),
                "items", items, "metrics", metrics));
        }
    }

    /** 批量任务先发现书籍，再由服务端按去重结果逐本下发。 */
    private void runBatchTask(Task task, Map<String, Object> selectors) throws Exception {
        Map<String, Object> bookList = map(selectors, "bookList");
        String itemSelector = requiredSelector(bookList, "item");
        String titleSelector = requiredSelector(bookList, "title");
        String listUrl = expandUrl(value(task.catalogUrlTemplate, task.sourceWorkUrl), task.sourceWorkUrl, "", 0);
        if (listUrl.isBlank()) listUrl = task.sourceWorkUrl;
        validatePublicSameHost(listUrl, task.sourceWorkUrl);
        String html = fetchWithRetry(task, listUrl);
        List<Map<String, Object>> books = extractBooks(html, bookList, itemSelector, titleSelector, listUrl, task.categoryName);
        if (task.bookLimit > 0 && books.size() > task.bookLimit) {
            books = new ArrayList<>(books.subList(0, task.bookLimit));
        }
        if (books.isEmpty()) throw new IllegalStateException("book list contains no books");
        JsonNode discoveryAck = post("/reader/worker/source/runs/" + task.runId + "/books", Map.of(
            "runId", task.runId, "runToken", task.runToken, "workerId", workerId,
            "batchId", "java-books-" + task.runId + "-" + System.nanoTime(), "books", books, "completed", true));
        if (isTerminal(discoveryAck)) return;
        while (true) {
            JsonNode next = post("/reader/worker/source/runs/" + task.runId + "/books/claim",
                Map.of("runId", task.runId, "runToken", task.runToken, "workerId", workerId));
            if (next == null || next.isNull()) return;
            Task bookTask = mapper.convertValue(next, Task.class);
            runSingleBookTask(bookTask, selectors);
        }
    }

    private boolean isTerminal(JsonNode ack) {
        String runStatus = ack == null ? "" : ack.path("runStatus").asText("");
        String taskStatus = ack == null ? "" : ack.path("taskStatus").asText("");
        return "COMPLETED".equals(runStatus) || "FAILED".equals(runStatus) || "CANCELED".equals(runStatus)
            || "COMPLETED".equals(taskStatus) || "WAITING_REVIEW".equals(taskStatus);
    }

    private void runSingleBookTask(Task task, Map<String, Object> selectors) throws Exception {
        Map<String, Object> catalogSelector = map(selectors, "catalog");
        Map<String, Object> chapterSelector = map(selectors, "chapter");
        String sourceWorkUrl = resolveSourceWorkUrl(task, selectors);
        String catalogUrl = expandUrl(value(task.catalogUrlTemplate, sourceWorkUrl), sourceWorkUrl, "", 0);
        if (catalogUrl.isBlank()) catalogUrl = sourceWorkUrl;
        validatePublicSameHost(catalogUrl, sourceWorkUrl);
        String catalogHtml = fetchWithRetry(task, catalogUrl);
        Map<String, Object> metadata = extractWorkMetadata(catalogHtml, map(selectors, "detail"));
        List<Chapter> chapters = selectRange(extractCatalog(catalogHtml,
            requiredSelector(catalogSelector, "item"), requiredSelector(catalogSelector, "title"), catalogUrl,
            Boolean.parseBoolean(value(catalogSelector, "chapterNoFromUrl"))), task);
        if (chapters.isEmpty()) throw new IllegalStateException("catalog contains no chapters in the requested range");
        String contentSelector = requiredSelector(chapterSelector, "content");
        for (int offset = 0; offset < chapters.size();) {
            List<Map<String, Object>> items = new ArrayList<>();
            while (offset < chapters.size() && items.size() < BATCH_SIZE) {
                Chapter chapter = chapters.get(offset);
                String chapterUrl = expandUrl(value(task.chapterUrlTemplate, chapter.url), task.sourceWorkUrl, chapter.url, chapter.chapterNo);
                if (chapterUrl.isBlank()) chapterUrl = chapter.url;
                Map<String, Object> item = fetchChapter(task, chapterSelector, chapter, chapterUrl);
                insertDiscoveredNextChapter(chapters, offset, item, task);
                item.put("taskBookId", task.taskBookId);
                item.putAll(metadata);
                items.add(item);
                offset++;
                politeDelay(task);
            }
            post("/reader/worker/source/runs/" + task.runId + "/result", Map.of(
                "runId", task.runId, "runToken", task.runToken, "workerId", workerId, "executorType", "JAVA",
                "batchId", "java-book-" + task.runId + "-" + System.nanoTime(), "ruleVersion", task.ruleVersion,
                "cursorChapterNo", items.getLast().get("chapterNo"), "completed", offset == chapters.size(), "items", items,
                "metrics", Map.of("requests", items.size() + 1, "success", items.size())));
        }
    }

    private List<Map<String, Object>> extractBooks(String html, Map<String, Object> selectors, String itemSelector,
                                                     String titleSelector, String baseUrl, String defaultCategory) {
        Document document = Jsoup.parse(html, baseUrl);
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        String baseHost = URI.create(baseUrl).getHost();
        List<Element> nodes = new ArrayList<>();
        String groupSelector = value(selectors, "group");
        if (groupSelector.isBlank()) {
            nodes.addAll(document.select(itemSelector));
        } else {
            String groupTitleSelector = value(selectors, "groupTitle");
            if (groupTitleSelector.isBlank()) groupTitleSelector = "h1,h2,h3";
            for (Element group : document.select(groupSelector)) {
                Element title = group.selectFirst(groupTitleSelector);
                String groupTitle = clean(title == null ? "" : title.text());
                if (defaultCategory == null || defaultCategory.isBlank() || groupTitle.contains(defaultCategory) || defaultCategory.contains(groupTitle)) {
                    nodes.addAll(group.select(itemSelector));
                }
            }
        }
        for (Element node : nodes) {
            Element anchor = node.selectFirst("a[href]");
            if (anchor == null && "a".equalsIgnoreCase(node.tagName()) && node.hasAttr("href")) anchor = node;
            if (anchor == null) continue;
            String sourceWorkUrl = anchor.absUrl("href");
            // Ranking pages can include partner links; keep only this source's
            // host before submitting books to the backend.
            if (baseHost != null && !sameAuthorizedHost(URI.create(sourceWorkUrl).getHost(), baseHost)) continue;
            if (sourceWorkUrl.isBlank() || !seenUrls.add(sourceWorkUrl)) continue;
            Element titleNode = node.selectFirst(titleSelector);
            String title = clean(titleNode == null ? anchor.text() : titleNode.text());
            if (title.isBlank()) continue;
            String latestText = selectorText(node, selectors, "latestChapterNo", "");
            int latestNo = chapterNumber(latestText, 0);
            Element latestNode = value(selectors, "latestChapterNo").isBlank() ? null : node.selectFirst(value(selectors, "latestChapterNo"));
            if (latestNode != null && latestNode.hasAttr("href")) {
                latestNo = chapterNumberFromUrl(latestNode.absUrl("href"), latestText, latestNo);
            }
            Map<String, Object> book = new HashMap<>();
            book.put("sourceWorkUrl", sourceWorkUrl);
            book.put("sourceWorkTitle", title);
            book.put("authorName", clean(selectorText(node, selectors, "author", "未知作者")));
            book.put("categoryName", clean(selectorText(node, selectors, "category", defaultCategory)));
            book.put("serialStatus", normalizeSerialStatus(selectorText(node, selectors, "serialStatus", "")));
            book.put("remoteLatestChapterNo", latestNo);
            book.put("remoteChapterCount", latestNo);
            result.add(book);
        }
        return result;
    }

    private void enrichBook(Task task, Map<String, Object> selectors, Map<String, Object> book) throws Exception {
        String workUrl = value(book.get("sourceWorkUrl"), "");
        String html = fetchWithRetry(task, workUrl);
        book.putAll(extractWorkMetadata(html, map(selectors, "detail")));
        Map<String, Object> catalog = map(selectors, "catalog");
        List<Chapter> chapters = extractCatalog(html, requiredSelector(catalog, "item"), requiredSelector(catalog, "title"), workUrl,
            Boolean.parseBoolean(value(catalog, "chapterNoFromUrl")));
        int latest = chapters.stream().mapToInt(chapter -> chapter.chapterNo).max().orElse(chapters.size());
        if (latest > 0) {
            book.put("remoteLatestChapterNo", latest);
            book.put("remoteChapterCount", latest);
        }
        politeDelay(task);
    }

    private Map<String, Object> extractWorkMetadata(String html, Map<String, Object> selectors) {
        if (selectors.isEmpty()) return Map.of();
        Document document = Jsoup.parse(html);
        Map<String, Object> result = new HashMap<>();
        String author = selectorValue(document, selectors, "author");
        String category = selectorValue(document, selectors, "category");
        String serialStatus = normalizeSerialStatus(selectorValue(document, selectors, "serialStatus"));
        if (!author.isBlank()) result.put("authorName", author);
        if (!category.isBlank()) result.put("categoryName", category);
        if (!serialStatus.isBlank()) result.put("serialStatus", serialStatus);
        return result;
    }

    private static String selectorValue(Element scope, Map<String, Object> selectors, String key) {
        String selector = value(selectors, key);
        if (selector.isBlank()) return "";
        Element match = scope.selectFirst(selector);
        if (match == null) return "";
        String attr = value(selectors, key + "Attr");
        return clean(attr.isBlank() ? match.text() : match.attr(attr));
    }

    private static String normalizeSerialStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (List.of("FINISHED", "COMPLETED", "完结", "全本", "大结局").stream().anyMatch(normalized::contains)) return "FINISHED";
        if (List.of("ONGOING", "SERIAL", "连载").stream().anyMatch(normalized::contains)) return "ONGOING";
        return "";
    }

    private static String selectorText(Element node, Map<String, Object> selectors, String key, String fallback) {
        String selector = value(selectors, key);
        Element match = selector.isBlank() ? null : node.selectFirst(selector);
        return match == null ? fallback : match.text();
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
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
        String metaPrefix = new String(response.body(), StandardCharsets.ISO_8859_1).substring(0, Math.min(response.body().length, 8192)).toLowerCase();
        Charset charset = contentType.contains("gbk") || contentType.contains("gb2312")
            || metaPrefix.contains("charset=gbk") || metaPrefix.contains("charset=gb18030") || metaPrefix.contains("charset=gb2312")
            ? Charset.forName("GB18030") : StandardCharsets.UTF_8;
        return new String(response.body(), charset);
    }

    private String fetchFormWithRetry(Task task, String target, Map<String, String> params) throws Exception {
        int retries = Math.min(5, Math.max(0, task.maxRetries));
        for (int attempt = 0; attempt <= retries; attempt++) {
            waitForPermit(task);
            try {
                String form = params.entrySet().stream()
                    .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
                HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofMillis(Math.max(1000, task.connectTimeoutMs + task.readTimeoutMs)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    .header("User-Agent", "reader-source-worker/1.0 (+authorized-fetch)")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new SourceHttpException(response.statusCode(), response.headers().firstValue("Retry-After").orElse(""),
                        "source returned HTTP " + response.statusCode());
                }
                if (response.body().length > MAX_RESPONSE_BYTES) throw new IOException("source response exceeds size limit");
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
                String metaPrefix = new String(response.body(), StandardCharsets.ISO_8859_1)
                    .substring(0, Math.min(response.body().length, 8192)).toLowerCase();
                Charset charset = contentType.contains("gbk") || contentType.contains("gb2312")
                    || metaPrefix.contains("charset=gbk") || metaPrefix.contains("charset=gb18030") || metaPrefix.contains("charset=gb2312")
                    ? Charset.forName("GB18030") : StandardCharsets.UTF_8;
                return new String(response.body(), charset);
            } catch (SourceHttpException ex) {
                if (ex.status == 401 || ex.status == 403 || ex.status == 429 || attempt >= retries) throw ex;
                Thread.sleep((1L << attempt) * 1000L);
            }
        }
        throw new IllegalStateException("request retry exhausted");
    }

    private void waitForPermit(Task task) throws Exception {
        while (true) {
            JsonNode permit = post("/reader/worker/source/runs/" + task.runId + "/permit", Map.of(
                "runId", task.runId, "runToken", task.runToken, "workerId", workerId));
            if (permit.path("allowed").asBoolean()) return;
            if (permit.path("reason").asText("").contains("每日请求上限")) {
                throw new IOException(permit.path("reason").asText("站点每日请求上限已耗尽"));
            }
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

    private List<Chapter> extractCatalog(String html, String itemSelector, String titleSelector, String baseUrl,
                                         boolean chapterNoFromUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        List<Chapter> result = new ArrayList<>();
        Map<String, Boolean> seenUrls = new HashMap<>();
        int index = 0;
        for (Element node : document.select(itemSelector)) {
            Element anchor = node.select("a[href]").first();
            if (anchor == null && node.is("a[href]")) anchor = node;
            if (anchor == null) continue;
            String url = anchor.absUrl("href");
            if (url.isBlank() || seenUrls.putIfAbsent(url, Boolean.TRUE) != null) continue;
            Element title = node.select(titleSelector).first();
            String name = clean((title == null ? anchor : title).text());
            result.add(new Chapter(url, chapterNumberFromUrl(url, chapterNoFromUrl ? "" : name, ++index), name));
        }
        result.sort(Comparator.comparingInt(chapter -> chapter.chapterNo));
        return result;
    }

    private Map<String, Object> extractChapter(String html, String titleSelector, String contentSelector, Chapter chapter, String url) throws Exception {
        Document document = Jsoup.parse(html, url);
        Element content = document.select(contentSelector).first();
        if (content == null || clean(content.text()).isBlank()) throw new IllegalStateException("chapter content selector matched no element");
        String title = titleSelector == null || titleSelector.isBlank() ? chapter.name : clean(document.select(titleSelector).first() == null ? chapter.name : document.select(titleSelector).first().text());
        String text = ChapterTextFormatter.format(content.wholeText());
        Map<String, Object> result = new HashMap<>();
        result.put("sourceChapterId", chapter.url);
        result.put("sourceUrl", url);
        result.put("chapterNo", chapter.chapterNo);
        result.put("chapterName", title);
        result.put("content", text);
        result.put("contentHash", sha256(text));
        return result;
    }

    private Map<String, Object> fetchChapter(Task task, Map<String, Object> selectors, Chapter chapter, String sourceUrl) throws Exception {
        String currentUrl = sourceUrl;
        Set<String> seen = new HashSet<>();
        int maxPages = 10;
        try {
            String configured = value(selectors, "maxPages");
            if (!configured.isBlank()) maxPages = Integer.parseInt(configured);
        } catch (NumberFormatException ignored) {
            // Use the bounded default for an invalid optional value.
        }
        maxPages = Math.min(20, Math.max(1, maxPages));
        List<String> parts = new ArrayList<>();
        Map<String, Object> item = null;
        String nextChapterUrl = "";
        for (int page = 0; page < maxPages; page++) {
            if (!seen.add(currentUrl)) throw new IllegalStateException("chapter pagination contains a cycle");
            String html = fetchWithRetry(task, currentUrl);
            String nextUrl = "";
            String nextSelector = value(selectors, "nextPage");
            String nextChapterSelector = value(selectors, "nextChapter");
            if (!nextSelector.isBlank() || !nextChapterSelector.isBlank()) {
                Document document = Jsoup.parse(html, currentUrl);
                if (!nextSelector.isBlank()) {
                    Element next = document.selectFirst(nextSelector);
                    if (next != null && next.hasAttr("href")) {
                        nextUrl = next.absUrl("href");
                        next.remove();
                    }
                }
                if (nextUrl.isBlank() && !nextChapterSelector.isBlank()) {
                    Element nextChapter = document.selectFirst(nextChapterSelector);
                    String requiredText = value(selectors, "nextChapterText");
                    if (nextChapter != null && nextChapter.hasAttr("href")
                        && (requiredText.isBlank() || clean(nextChapter.text()).contains(requiredText))) {
                        nextChapterUrl = nextChapter.absUrl("href");
                        validatePublicSameHost(nextChapterUrl, task.sourceWorkUrl);
                    }
                }
                document.select("script,style").remove();
                html = document.outerHtml();
            }
            item = extractChapter(html, value(selectors, "title"), requiredSelector(selectors, "content"), chapter, sourceUrl);
            parts.add(value(item.get("content"), ""));
            if (nextUrl.isBlank()) break;
            if (page + 1 >= maxPages) throw new IllegalStateException("chapter pagination exceeds maxPages");
            validatePublicSameHost(nextUrl, task.sourceWorkUrl);
            politeDelay(task);
            currentUrl = nextUrl;
        }
        if (item == null) throw new IllegalStateException("chapter content selector matched no element");
        String text = ChapterTextFormatter.format(String.join("\n\n", parts));
        if (chapter.discoveredNext) {
            String actualTitle = value(item.get("chapterName"), "")
                .replaceFirst("[（(]\\s*\\d+\\s*/\\s*\\d+\\s*[）)]\\s*$", "").trim();
            if (actualTitle.isBlank()) actualTitle = "第" + chapter.chapterNo + "章";
            item.put("chapterName", actualTitle);
            item.put("chapterNo", chapterNumber(actualTitle, chapter.chapterNo));
        } else {
            item.put("chapterName", chapter.name);
        }
        item.put("content", text);
        item.put("contentHash", sha256(text));
        if (!nextChapterUrl.isBlank()) item.put("_nextChapterUrl", nextChapterUrl);
        return item;
    }

    private void insertDiscoveredNextChapter(List<Chapter> chapters, int index, Map<String, Object> item, Task task) {
        String nextUrl = value(item.remove("_nextChapterUrl"), "").trim();
        if (nextUrl.isBlank() || chapters.stream().anyMatch(chapter -> chapter.url.equals(nextUrl))) return;
        int currentNo = item.get("chapterNo") instanceof Number number ? number.intValue() : chapters.get(index).chapterNo;
        int nextNo = currentNo + 1;
        if (task.endChapterNo != null && nextNo > task.endChapterNo) return;
        chapters.add(index + 1, new Chapter(nextUrl, nextNo, "", true));
    }

    private List<Chapter> selectRange(List<Chapter> chapters, Task task) {
        int start = Math.max(task.cursorChapterNo + 1, task.startChapterNo);
        return chapters.stream().filter(c -> c.chapterNo >= start && (task.endChapterNo == null || c.chapterNo <= task.endChapterNo)).toList();
    }

    private void validatePublicSameHost(String target, String approved) throws Exception {
        URI got = URI.create(target), source = URI.create(approved);
        if (!(got.getScheme().equalsIgnoreCase("http") || got.getScheme().equalsIgnoreCase("https")) || got.getHost() == null || got.getUserInfo() != null) throw new IOException("source URL must be a credential-free HTTP(S) URL");
        if (!sameAuthorizedHost(got.getHost(), source.getHost())) throw new IOException("source URL leaves the approved host");
        if (allowPrivateForTest) return;
        for (InetAddress address : InetAddress.getAllByName(got.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) throw new IOException("source URL resolves to a non-public address");
        }
    }

    private static boolean sameAuthorizedHost(String actual, String expected) {
        if (actual == null || expected == null) return false;
        String left = actual.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        String right = expected.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return left.equals(right)
            || left.replaceFirst("^www\\.", "").equals(right.replaceFirst("^www\\.", ""));
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
        String value = template.replace("{url}", workUrl).replace("{workUrl}", workUrl)
            .replace("{workId}", id).replace("{chapterUrl}", chapterUrl == null ? "" : chapterUrl)
            .replace("{id}", chapterId).replace("{chapterId}", chapterId).replace("{chapterNo}", String.valueOf(chapterNo));
        return work.resolve(value).toString();
    }

    /** Resolve a fallback route through the site's declared search contract before reading its catalog. */
    private String resolveSourceWorkUrl(Task task, Map<String, Object> selectors) throws Exception {
        if (task.fallbackId == 0 || task.searchUrlTemplate == null || task.searchUrlTemplate.isBlank()) {
            return task.sourceWorkUrl;
        }
        Map<String, Object> search = map(selectors, "search");
        String searchTemplate = value(search, "url");
        if (searchTemplate.isBlank()) searchTemplate = task.searchUrlTemplate;
        String searchUrl = expandUrl(expandSearchTemplate(searchTemplate, task), task.sourceWorkUrl, "", 0);
        validatePublicSameHost(searchUrl, task.sourceWorkUrl);
        String method = value(search, "method").toUpperCase(Locale.ROOT);
        String html;
        Map<String, String> params = new HashMap<>();
        Object rawParams = search.get("params");
        if (rawParams instanceof Map<?, ?> declaredParams) {
            for (Map.Entry<?, ?> entry : declaredParams.entrySet()) {
                params.put(String.valueOf(entry.getKey()), expandSearchValue(entry.getValue(), task));
            }
        }
        if ("POST".equals(method)) {
            html = fetchFormWithRetry(task, searchUrl, params);
        } else {
            html = fetchWithRetry(task, appendQuery(searchUrl, params));
        }
        Map<String, Object> bookList = map(selectors, "bookList");
        String itemSelector = requiredSelector(bookList, "item");
        String titleSelector = requiredSelector(bookList, "title");
        List<Map<String, Object>> books = extractBooks(html, bookList, itemSelector, titleSelector, searchUrl, "");
        String targetTitle = clean(task.sourceWorkTitle).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> candidates = books.stream()
            .filter(book -> targetTitle.equals(clean(value(book.get("sourceWorkTitle"), "")).toLowerCase(Locale.ROOT)))
            .toList();
        if (candidates.isEmpty()) {
            candidates = books.stream()
                .filter(book -> clean(value(book.get("sourceWorkTitle"), "")).toLowerCase(Locale.ROOT).contains(targetTitle))
                .toList();
        }
        if (candidates.isEmpty()) throw new IllegalStateException("fallback search found no matching work");
        return value(candidates.getFirst().get("sourceWorkUrl"), "");
    }

    private static String expandSearchTemplate(String template, Task task) {
        return template.replace("{title}", urlEncode(task.sourceWorkTitle))
            .replace("{author}", urlEncode(task.authorName));
    }

    private static String expandSearchValue(Object source, Task task) {
        return String.valueOf(source == null ? "" : source)
            .replace("{title}", task.sourceWorkTitle == null ? "" : task.sourceWorkTitle)
            .replace("{author}", task.authorName == null ? "" : task.authorName);
    }

    private static String appendQuery(String url, Map<String, String> params) {
        if (params.isEmpty()) return url;
        StringBuilder query = new StringBuilder(url);
        query.append(url.contains("?") ? '&' : '?');
        params.forEach((key, value) -> {
            if (query.charAt(query.length() - 1) != '?' && query.charAt(query.length() - 1) != '&') query.append('&');
            query.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return query.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder("sha256:");
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }

    private static int chapterNumber(String title, int fallback) {
        Matcher matcher = CHAPTER_NUMBER.matcher(title);
        if (!matcher.find()) return fallback;
        String token = matcher.group(1);
        if (token.chars().allMatch(Character::isDigit)) return Integer.parseInt(token);
        return chineseNumber(token, fallback);
    }

    private static int chineseNumber(String value, int fallback) {
        Map<Character, Integer> digits = Map.ofEntries(Map.entry('零', 0), Map.entry('〇', 0), Map.entry('一', 1),
            Map.entry('二', 2), Map.entry('两', 2), Map.entry('三', 3), Map.entry('四', 4), Map.entry('五', 5),
            Map.entry('六', 6), Map.entry('七', 7), Map.entry('八', 8), Map.entry('九', 9));
        Map<Character, Integer> units = Map.of('十', 10, '百', 100, '千', 1000, '万', 10000, '亿', 100000000);
        long total = 0, section = 0, number = 0;
        for (char current : value.toCharArray()) {
            if (digits.containsKey(current)) number = digits.get(current);
            else if (units.containsKey(current)) {
                int unit = units.get(current);
                if (unit < 10000) section += (number == 0 ? 1 : number) * unit;
                else { total += (section + number) * unit; section = 0; number = 0; }
            } else return fallback;
        }
        long result = total + section + number;
        return result > Integer.MAX_VALUE ? fallback : (int) result;
    }

    private static int chapterNumberFromUrl(String rawUrl, String title, int fallback) {
        int titleNumber = chapterNumber(title, 0);
        if (titleNumber > 0) return titleNumber;
        try {
            String path = URI.create(rawUrl).getPath().replaceAll("/$", "");
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.toLowerCase().endsWith(".html")) last = last.substring(0, last.length() - 5);
            if (last.matches("\\d{1,7}")) return Integer.parseInt(last);
        } catch (RuntimeException ignored) {
            // Fall back to the title when a source uses a non-numeric chapter URL.
        }
        return fallback;
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
        private final boolean discoveredNext;
        private Chapter(String url, int chapterNo, String name) { this(url, chapterNo, name, false); }
        private Chapter(String url, int chapterNo, String name, boolean discoveredNext) {
            this.url = Objects.requireNonNull(url);
            this.chapterNo = chapterNo;
            this.name = name;
            this.discoveredNext = discoveredNext;
        }
    }

    public static final class Task {
        public long runId, taskId, siteId, ruleId, taskBookId, workId;
        public String runToken, workerId, executorType, collectionMode, categoryName, sourceWorkUrl, sourceWorkTitle, authorName, searchUrlTemplate, catalogUrlTemplate, chapterUrlTemplate, selectorJson, honorRetryAfter;
        public long fallbackId;
        public int ruleVersion, cursorChapterNo, startChapterNo, maxRetries, connectTimeoutMs, readTimeoutMs, minDelayMs, maxDelayMs, claimLeaseSeconds, concurrencyLimit, requestsPerMinute, dailyRequestLimit, circuitBreakerThreshold, bookLimit;
        public Integer endChapterNo;
    }
}
