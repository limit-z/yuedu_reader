package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

const batchSize = 20

type worker struct {
	baseURL             string
	secret              string
	workerID            string
	client              *http.Client
	allowPrivateForTest bool
}

type sourceHTTPError struct {
	status     int
	retryAfter string
	message    string
}

func (e sourceHTTPError) Error() string { return e.message }

type task struct {
	RunID              flexibleInt64 `json:"runId"`
	RunToken           string        `json:"runToken"`
	SourceWorkURL      string        `json:"sourceWorkUrl"`
	CatalogURLTemplate string        `json:"catalogUrlTemplate"`
	ChapterURLTemplate string        `json:"chapterUrlTemplate"`
	SelectorJSON       string        `json:"selectorJson"`
	RuleVersion        int           `json:"ruleVersion"`
	CursorChapterNo    int           `json:"cursorChapterNo"`
	StartChapterNo     int           `json:"startChapterNo"`
	EndChapterNo       *int          `json:"endChapterNo"`
	MinDelayMs         int           `json:"minDelayMs"`
	MaxDelayMs         int           `json:"maxDelayMs"`
	MaxRetries         int           `json:"maxRetries"`
	ConnectTimeoutMs   int           `json:"connectTimeoutMs"`
	ReadTimeoutMs      int           `json:"readTimeoutMs"`
	ClaimLeaseSeconds  int           `json:"claimLeaseSeconds"`
}

// Jackson may serialize Java Long values as JSON strings; accept both forms.
type flexibleInt64 int64

func (value *flexibleInt64) UnmarshalJSON(raw []byte) error {
	text := strings.Trim(string(raw), "\"")
	parsed, err := strconv.ParseInt(text, 10, 64)
	if err != nil {
		return fmt.Errorf("invalid int64 value %q: %w", text, err)
	}
	*value = flexibleInt64(parsed)
	return nil
}

type selectors struct {
	Catalog struct {
		Item  string `json:"item"`
		Title string `json:"title"`
	} `json:"catalog"`
	Chapter struct {
		Title   string `json:"title"`
		Content string `json:"content"`
	} `json:"chapter"`
}

type chapter struct {
	SourceChapterID string `json:"sourceChapterId"`
	URL             string `json:"url"`
	ChapterNo       int    `json:"chapterNo"`
	ChapterName     string `json:"chapterName"`
}

func main() {
	secret := os.Getenv("READER_SOURCE_WORKER_SECRET")
	if secret == "" {
		log.Fatal("READER_SOURCE_WORKER_SECRET is required")
	}
	workerID := os.Getenv("READER_SOURCE_WORKER_ID")
	if workerID == "" {
		workerID = "go-worker"
	}
	baseURL := strings.TrimRight(env("READER_SOURCE_API_BASE_URL", "http://127.0.0.1:8080"), "/")
	w := &worker{baseURL: baseURL, secret: secret, workerID: workerID, client: &http.Client{Timeout: 30 * time.Second}, allowPrivateForTest: strings.EqualFold(os.Getenv("READER_SOURCE_ALLOW_PRIVATE_FOR_TEST"), "true")}
	for {
		var claimed task
		if err := w.post("/reader/worker/source/runs/claim", map[string]any{"executorType": "GO", "workerId": workerID}, &claimed); err != nil {
			log.Printf("claim failed: %v", err)
			time.Sleep(5 * time.Second)
			continue
		}
		if claimed.RunID == 0 {
			time.Sleep(5 * time.Second)
			continue
		}
		log.Printf("claimed run=%d", claimed.RunID)
		if err := w.run(&claimed); err != nil {
			log.Printf("run=%d stopped: %v", claimed.RunID, err)
			w.reportError(&claimed, err, claimed.SourceWorkURL, 0)
		}
	}
}

func (w *worker) run(t *task) error {
	heartbeatStop := make(chan struct{})
	go w.heartbeat(t, heartbeatStop)
	defer close(heartbeatStop)

	var rule selectors
	if err := json.Unmarshal([]byte(t.SelectorJSON), &rule); err != nil {
		return fmt.Errorf("invalid selector JSON: %w", err)
	}
	if rule.Catalog.Item == "" || rule.Catalog.Title == "" || rule.Chapter.Content == "" {
		return errors.New("catalog.item, catalog.title and chapter.content CSS selectors are required")
	}
	catalogURL := expandURL(t.CatalogURLTemplate, t.SourceWorkURL, "", 0)
	if catalogURL == "" {
		catalogURL = t.SourceWorkURL
	}
	if err := validatePublicSameHost(catalogURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
		return err
	}
	catalogHTML, _, err := w.fetch(t, catalogURL)
	if err != nil {
		return err
	}
	chapters, err := extractCatalog(catalogHTML, rule.Catalog.Item, rule.Catalog.Title, catalogURL)
	if err != nil {
		return err
	}
	chapters = selectRange(chapters, t)
	if len(chapters) == 0 {
		return errors.New("catalog contains no chapters in the requested range")
	}
	for offset := 0; offset < len(chapters); offset += batchSize {
		end := offset + batchSize
		if end > len(chapters) {
			end = len(chapters)
		}
		items := make([]map[string]any, 0, end-offset)
		for _, c := range chapters[offset:end] {
			chapterURL := expandURL(t.ChapterURLTemplate, t.SourceWorkURL, c.URL, c.ChapterNo)
			if chapterURL == "" {
				chapterURL = c.URL
			}
			if err := validatePublicSameHost(chapterURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
				return err
			}
			body, _, fetchErr := w.fetch(t, chapterURL)
			if fetchErr != nil {
				return fetchErr
			}
			item, parseErr := extractChapter(body, rule.Chapter.Title, rule.Chapter.Content, c, chapterURL)
			if parseErr != nil {
				return parseErr
			}
			items = append(items, item)
			politeDelay(t)
		}
		if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/result", t.RunID), map[string]any{
			"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID, "executorType": "GO",
			"batchId": fmt.Sprintf("go-%d-%d", t.RunID, time.Now().UnixNano()), "ruleVersion": t.RuleVersion,
			"cursorChapterNo": chapters[end-1].ChapterNo, "completed": end == len(chapters), "items": items,
			"metrics": map[string]int{"requests": len(items) + 1, "success": len(items)},
		}, nil); err != nil {
			return err
		}
	}
	return nil
}

func (w *worker) heartbeat(t *task, stop <-chan struct{}) {
	interval := t.ClaimLeaseSeconds / 3
	if interval < 10 {
		interval = 10
	}
	ticker := time.NewTicker(time.Duration(interval) * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/heartbeat", t.RunID), map[string]any{
				"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID,
			}, nil); err != nil {
				log.Printf("heartbeat failed run=%d: %v", t.RunID, err)
				return
			}
		case <-stop:
			return
		}
	}
}

func (w *worker) fetch(t *task, target string) (string, int, error) {
	for attempt := 0; attempt <= min(t.MaxRetries, 5); attempt++ {
		var permit map[string]any
		if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/permit", t.RunID), map[string]any{"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID}, &permit); err != nil {
			return "", 0, err
		}
		if allowed, _ := permit["allowed"].(bool); !allowed {
			delay := number(permit["retryAfterMs"], 1000)
			time.Sleep(time.Duration(min(delay, 300000)) * time.Millisecond)
			continue
		}
		req, _ := http.NewRequest(http.MethodGet, target, nil)
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
		req.Header.Set("User-Agent", "reader-source-worker/1.0 (+authorized-fetch)")
		resp, err := w.client.Do(req)
		if err != nil {
			if attempt < min(t.MaxRetries, 5) {
				time.Sleep(time.Duration(1<<attempt) * time.Second)
				continue
			}
			return "", 0, err
		}
		defer resp.Body.Close()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
			err := sourceHTTPError{status: resp.StatusCode, retryAfter: resp.Header.Get("Retry-After"), message: fmt.Sprintf("source returned HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))}
			if resp.StatusCode == 401 || resp.StatusCode == 403 || resp.StatusCode == 429 || attempt >= min(t.MaxRetries, 5) {
				return "", resp.StatusCode, err
			}
			time.Sleep(time.Duration(1<<attempt) * time.Second)
			continue
		}
		body, err := io.ReadAll(io.LimitReader(resp.Body, 8*1024*1024+1))
		if err != nil {
			return "", resp.StatusCode, err
		}
		if len(body) > 8*1024*1024 {
			return "", resp.StatusCode, errors.New("source response exceeds size limit")
		}
		return string(body), resp.StatusCode, nil
	}
	return "", 0, errors.New("request retry exhausted")
}

func (w *worker) reportError(t *task, err error, sourceURL string, status int) {
	httpError, isHTTPError := err.(sourceHTTPError)
	if isHTTPError {
		status = httpError.status
	}
	errorType := "PARSE"
	if status != 0 {
		errorType = "HTTP"
	}
	payload := map[string]any{
		"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID, "errorType": errorType,
		"sourceUrl": sourceURL, "message": truncate(err.Error(), 500),
	}
	if status != 0 {
		payload["httpStatus"] = status
	}
	if isHTTPError && httpError.retryAfter != "" {
		if seconds, parseErr := strconv.Atoi(strings.TrimSpace(httpError.retryAfter)); parseErr == nil && seconds >= 0 {
			payload["retryAt"] = time.Now().Add(time.Duration(seconds) * time.Second).Format("2006-01-02T15:04:05")
		}
	}
	_ = w.post(fmt.Sprintf("/reader/worker/source/runs/%d/error", t.RunID), payload, nil)
}

func (w *worker) post(path string, payload any, out any) error {
	body, _ := json.Marshal(payload)
	req, err := http.NewRequest(http.MethodPost, w.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Reader-Worker-Secret", w.secret)
	resp, err := w.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("worker API returned HTTP %d", resp.StatusCode)
	}
	var envelope struct {
		Code int             `json:"code"`
		Msg  string          `json:"msg"`
		Data json.RawMessage `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&envelope); err != nil {
		return err
	}
	if envelope.Code != 0 && envelope.Code != 200 {
		return errors.New(envelope.Msg)
	}
	if out != nil && len(envelope.Data) > 0 && string(envelope.Data) != "null" {
		return json.Unmarshal(envelope.Data, out)
	}
	return nil
}

func extractCatalog(raw, itemSelector, titleSelector, baseURL string) ([]chapter, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil, err
	}
	result := make([]chapter, 0)
	doc.Find(itemSelector).Each(func(index int, selection *goquery.Selection) {
		href, ok := selection.Find("a[href]").First().Attr("href")
		if !ok && goquery.NodeName(selection) == "a" {
			href, ok = selection.Attr("href")
		}
		if !ok {
			return
		}
		resolved, resolveErr := url.Parse(baseURL)
		if resolveErr != nil {
			return
		}
		link, linkErr := url.Parse(href)
		if linkErr != nil {
			return
		}
		resolved = resolved.ResolveReference(link)
		title := strings.TrimSpace(selection.Find(titleSelector).First().Text())
		if title == "" {
			title = strings.TrimSpace(selection.Find("a").First().Text())
		}
		result = append(result, chapter{SourceChapterID: resolved.String(), URL: resolved.String(), ChapterNo: chapterNumber(title, index+1), ChapterName: title})
	})
	return result, nil
}

func extractChapter(raw, titleSelector, contentSelector string, c chapter, sourceURL string) (map[string]any, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil, err
	}
	content := strings.TrimSpace(doc.Find(contentSelector).First().Text())
	if content == "" {
		return nil, errors.New("chapter content selector matched no element")
	}
	title := strings.TrimSpace(doc.Find(titleSelector).First().Text())
	if title == "" {
		title = c.ChapterName
	}
	digest := sha256.Sum256([]byte(content))
	return map[string]any{"sourceChapterId": c.SourceChapterID, "sourceUrl": sourceURL, "chapterNo": c.ChapterNo, "chapterName": title, "content": content, "contentHash": "sha256:" + hex.EncodeToString(digest[:])}, nil
}

func selectRange(chapters []chapter, t *task) []chapter {
	start := t.CursorChapterNo + 1
	if start < t.StartChapterNo {
		start = t.StartChapterNo
	}
	result := make([]chapter, 0)
	for _, c := range chapters {
		if c.ChapterNo >= start && (t.EndChapterNo == nil || c.ChapterNo <= *t.EndChapterNo) {
			result = append(result, c)
		}
	}
	return result
}

func expandURL(template, workURL, chapterURL string, chapterNo int) string {
	if template == "" {
		return ""
	}
	work, _ := url.Parse(workURL)
	id := ""
	if work != nil {
		parts := strings.Split(strings.Trim(work.Path, "/"), "/")
		if len(parts) > 0 {
			id = parts[len(parts)-1]
		}
	}
	chapterID := id
	if chapterURL != "" {
		parsed, _ := url.Parse(chapterURL)
		if parsed != nil {
			parts := strings.Split(strings.Trim(parsed.Path, "/"), "/")
			if len(parts) > 0 {
				chapterID = parts[len(parts)-1]
			}
		}
	}
	value := strings.NewReplacer("{url}", workURL, "{workUrl}", workURL, "{id}", chapterID, "{chapterId}", chapterID, "{chapterNo}", strconv.Itoa(chapterNo)).Replace(template)
	base, _ := url.Parse(workURL)
	path, _ := url.Parse(value)
	if base == nil || path == nil {
		return value
	}
	return base.ResolveReference(path).String()
}

func validatePublicSameHost(target, approved string, allowPrivateForTest bool) error {
	got, err := url.Parse(target)
	if err != nil || got.Scheme != "http" && got.Scheme != "https" || got.Hostname() == "" || got.User != nil {
		return errors.New("source URL must be a credential-free HTTP(S) URL")
	}
	allowed, err := url.Parse(approved)
	if err != nil || !strings.EqualFold(got.Hostname(), allowed.Hostname()) {
		return errors.New("source URL leaves the approved host")
	}
	if allowPrivateForTest {
		return nil
	}
	addresses, err := net.LookupIP(got.Hostname())
	if err != nil {
		return err
	}
	for _, address := range addresses {
		if address.IsPrivate() || address.IsLoopback() || address.IsLinkLocalUnicast() || address.IsMulticast() || address.IsUnspecified() {
			return errors.New("source URL resolves to a non-public address")
		}
	}
	return nil
}

func politeDelay(t *task) {
	if t.MaxDelayMs < t.MinDelayMs {
		t.MaxDelayMs = t.MinDelayMs
	}
	if t.MaxDelayMs > 0 {
		time.Sleep(time.Duration(t.MinDelayMs) * time.Millisecond)
	}
}

var chapterPattern = regexp.MustCompile(`(?:第\s*)?(\d{1,7})`)

func chapterNumber(title string, fallback int) int {
	match := chapterPattern.FindStringSubmatch(title)
	if len(match) == 2 {
		value, _ := strconv.Atoi(match[1])
		return value
	}
	return fallback
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func number(value any, fallback int) int {
	if numeric, ok := value.(float64); ok {
		return int(numeric)
	}
	return fallback
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func truncate(value string, limit int) string {
	if len(value) <= limit {
		return value
	}
	return value[:limit]
}
