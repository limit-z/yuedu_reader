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
	"os/exec"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
	"golang.org/x/net/html"
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
	TaskBookID         flexibleInt64 `json:"taskBookId"`
	CollectionMode     string        `json:"collectionMode"`
	CategoryName       string        `json:"categoryName"`
	RunToken           string        `json:"runToken"`
	SourceWorkURL      string        `json:"sourceWorkUrl"`
	SourceWorkTitle    string        `json:"sourceWorkTitle"`
	AuthorName         string        `json:"authorName"`
	SearchURLTemplate  string        `json:"searchUrlTemplate"`
	FallbackID         flexibleInt64 `json:"fallbackId"`
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
	BookLimit          int           `json:"bookLimit"`
}

// Jackson may serialize Java Long values as JSON strings; accept both forms.
type flexibleInt64 int64

func (value *flexibleInt64) UnmarshalJSON(raw []byte) error {
	text := strings.Trim(strings.TrimSpace(string(raw)), "\"")
	if text == "" || text == "null" {
		*value = 0
		return nil
	}
	parsed, err := strconv.ParseInt(text, 10, 64)
	if err != nil {
		return fmt.Errorf("invalid int64 value %q: %w", text, err)
	}
	*value = flexibleInt64(parsed)
	return nil
}

type selectors struct {
	Catalog struct {
		Item             string `json:"item"`
		Title            string `json:"title"`
		ChapterNoFromURL bool   `json:"chapterNoFromUrl"`
	} `json:"catalog"`
	Chapter struct {
		Title           string `json:"title"`
		Content         string `json:"content"`
		NextPage        string `json:"nextPage"`
		NextChapter     string `json:"nextChapter"`
		NextChapterText string `json:"nextChapterText"`
		MaxPages        int    `json:"maxPages"`
	} `json:"chapter"`
	BookList struct {
		Item            string `json:"item"`
		Title           string `json:"title"`
		Author          string `json:"author"`
		Category        string `json:"category"`
		LatestChapterNo string `json:"latestChapterNo"`
		SerialStatus    string `json:"serialStatus"`
		Group           string `json:"group"`
		GroupTitle      string `json:"groupTitle"`
	} `json:"bookList"`
	Detail struct {
		Author           string `json:"author"`
		AuthorAttr       string `json:"authorAttr"`
		Category         string `json:"category"`
		CategoryAttr     string `json:"categoryAttr"`
		SerialStatus     string `json:"serialStatus"`
		SerialStatusAttr string `json:"serialStatusAttr"`
	} `json:"detail"`
	Search struct {
		Method string         `json:"method"`
		URL    string         `json:"url"`
		Params map[string]any `json:"params"`
	} `json:"search"`
}

type chapter struct {
	SourceChapterID string `json:"sourceChapterId"`
	URL             string `json:"url"`
	ChapterNo       int    `json:"chapterNo"`
	ChapterName     string `json:"chapterName"`
	DiscoveredNext  bool   `json:"-"`
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
	if t.CollectionMode == "ALL" || t.CollectionMode == "CATEGORY" {
		return w.runBatch(t, &rule)
	}
	sourceWorkURL, err := w.resolveSourceWorkURL(t, &rule)
	if err != nil {
		return err
	}
	catalogURL := expandURL(t.CatalogURLTemplate, sourceWorkURL, "", 0)
	if catalogURL == "" {
		catalogURL = sourceWorkURL
	}
	if err := validatePublicSameHost(catalogURL, sourceWorkURL, w.allowPrivateForTest); err != nil {
		return err
	}
	catalogHTML, _, err := w.fetch(t, catalogURL)
	if err != nil {
		return err
	}
	chapters, err := extractCatalog(catalogHTML, rule.Catalog.Item, rule.Catalog.Title, catalogURL, rule.Catalog.ChapterNoFromURL)
	if err != nil {
		return err
	}
	chapters = selectRange(chapters, t)
	if len(chapters) == 0 {
		return errors.New("catalog contains no chapters in the requested range")
	}
	for offset := 0; offset < len(chapters); {
		items := make([]map[string]any, 0, batchSize)
		for offset < len(chapters) && len(items) < batchSize {
			c := chapters[offset]
			chapterURL := expandURL(t.ChapterURLTemplate, t.SourceWorkURL, c.URL, c.ChapterNo)
			if chapterURL == "" {
				chapterURL = c.URL
			}
			if err := validatePublicSameHost(chapterURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
				return err
			}
			item, parseErr := w.fetchChapter(t, &rule, c, chapterURL)
			if parseErr != nil {
				return parseErr
			}
			chapters = insertDiscoveredNextChapter(chapters, offset, item, t)
			items = append(items, item)
			offset++
			politeDelay(t)
		}
		if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/result", t.RunID), map[string]any{
			"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID, "executorType": "GO",
			"batchId": fmt.Sprintf("go-%d-%d", t.RunID, time.Now().UnixNano()), "ruleVersion": t.RuleVersion,
			"cursorChapterNo": items[len(items)-1]["chapterNo"], "completed": offset == len(chapters), "items": items,
			"metrics": map[string]int{"requests": len(items) + 1, "success": len(items)},
		}, nil); err != nil {
			return err
		}
	}
	return nil
}

// runBatch discovers the authorized site book list, then processes each server-assigned book.
func (w *worker) runBatch(t *task, rule *selectors) error {
	if rule.BookList.Item == "" || rule.BookList.Title == "" {
		return errors.New("batch collection requires bookList.item and bookList.title selectors")
	}
	listURL := expandURL(t.CatalogURLTemplate, t.SourceWorkURL, "", 0)
	if listURL == "" {
		listURL = t.SourceWorkURL
	}
	if err := validatePublicSameHost(listURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
		return err
	}
	html, _, err := w.fetch(t, listURL)
	if err != nil {
		return err
	}
	books := extractBooks(html, rule, listURL, t.CategoryName)
	if len(books) == 0 {
		return errors.New("book list contains no books")
	}
	limit := t.BookLimit
	if limit < 1 {
		limit = 1
	}
	if len(books) > limit {
		books = books[:limit]
	}
	var discoveryAck struct {
		RunStatus  string `json:"runStatus"`
		TaskStatus string `json:"taskStatus"`
	}
	if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/books", t.RunID), map[string]any{
		"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID,
		"batchId": fmt.Sprintf("go-books-%d-%d", t.RunID, time.Now().UnixNano()), "books": books, "completed": true,
	}, &discoveryAck); err != nil {
		return err
	}
	if discoveryAck.RunStatus == "COMPLETED" || discoveryAck.RunStatus == "FAILED" || discoveryAck.RunStatus == "CANCELED" || discoveryAck.TaskStatus == "COMPLETED" || discoveryAck.TaskStatus == "WAITING_REVIEW" {
		return nil
	}
	for {
		var next task
		if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/books/claim", t.RunID), map[string]any{
			"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID,
		}, &next); err != nil {
			return err
		}
		if next.RunID == 0 || next.TaskBookID == 0 {
			return nil
		}
		if err := w.runSingleBook(&next, rule); err != nil {
			return err
		}
	}
}

func (w *worker) runSingleBook(t *task, rule *selectors) error {
	sourceWorkURL, err := w.resolveSourceWorkURL(t, rule)
	if err != nil {
		return err
	}
	catalogURL := expandURL(t.CatalogURLTemplate, sourceWorkURL, "", 0)
	if catalogURL == "" {
		catalogURL = sourceWorkURL
	}
	html, _, err := w.fetch(t, catalogURL)
	if err != nil {
		return err
	}
	chapters, err := extractCatalog(html, rule.Catalog.Item, rule.Catalog.Title, catalogURL, rule.Catalog.ChapterNoFromURL)
	if err != nil {
		return err
	}
	chapters = selectRange(chapters, t)
	if len(chapters) == 0 {
		return errors.New("catalog contains no chapters in the requested range")
	}
	metadata := extractWorkMetadata(html, rule)
	for offset := 0; offset < len(chapters); {
		items := make([]map[string]any, 0, batchSize)
		for offset < len(chapters) && len(items) < batchSize {
			c := chapters[offset]
			chapterURL := expandURL(t.ChapterURLTemplate, sourceWorkURL, c.URL, c.ChapterNo)
			if chapterURL == "" {
				chapterURL = c.URL
			}
			item, parseErr := w.fetchChapter(t, rule, c, chapterURL)
			if parseErr != nil {
				return parseErr
			}
			chapters = insertDiscoveredNextChapter(chapters, offset, item, t)
			item["taskBookId"] = t.TaskBookID
			for key, value := range metadata {
				item[key] = value
			}
			items = append(items, item)
			offset++
			politeDelay(t)
		}
		if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/result", t.RunID), map[string]any{
			"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID, "executorType": "GO",
			"batchId": fmt.Sprintf("go-book-%d-%d", t.RunID, time.Now().UnixNano()), "ruleVersion": t.RuleVersion,
			"cursorChapterNo": items[len(items)-1]["chapterNo"], "completed": offset == len(chapters), "items": items,
			"metrics": map[string]int{"requests": len(items) + 1, "success": len(items)},
		}, nil); err != nil {
			return err
		}
	}
	return nil
}

func (w *worker) resolveSourceWorkURL(t *task, rule *selectors) (string, error) {
	if t.FallbackID == 0 || strings.TrimSpace(t.SearchURLTemplate) == "" {
		return t.SourceWorkURL, nil
	}
	searchTemplate := strings.TrimSpace(rule.Search.URL)
	if searchTemplate == "" {
		searchTemplate = t.SearchURLTemplate
	}
	searchURL := expandURL(searchTemplate, t.SourceWorkURL, "", 0)
	if err := validatePublicSameHost(searchURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
		return "", err
	}
	params := make(map[string]string)
	for key, raw := range rule.Search.Params {
		params[key] = expandSearchValue(raw, t)
	}
	var html string
	var err error
	if strings.EqualFold(rule.Search.Method, "POST") {
		html, err = w.fetchFormWithRetry(t, searchURL, params)
	} else {
		html, _, err = w.fetch(t, appendQuery(searchURL, params))
	}
	if err != nil {
		return "", err
	}
	books := extractBooks(html, rule, searchURL, "")
	title := strings.ToLower(strings.TrimSpace(t.SourceWorkTitle))
	for _, book := range books {
		bookTitle := strings.ToLower(strings.TrimSpace(fmt.Sprint(book["sourceWorkTitle"])))
		if bookTitle == title || strings.Contains(bookTitle, title) {
			return fmt.Sprint(book["sourceWorkUrl"]), nil
		}
	}
	return "", errors.New("fallback search found no matching work")
}

func expandSearchValue(value any, t *task) string {
	return strings.NewReplacer("{title}", t.SourceWorkTitle, "{author}", t.AuthorName).Replace(fmt.Sprint(value))
}

func appendQuery(rawURL string, params map[string]string) string {
	if len(params) == 0 {
		return rawURL
	}
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return rawURL
	}
	query := parsed.Query()
	for key, value := range params {
		query.Set(key, value)
	}
	parsed.RawQuery = query.Encode()
	return parsed.String()
}

func extractBooks(raw string, rule *selectors, baseURL, defaultCategory string) []map[string]any {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil
	}
	result := make([]map[string]any, 0)
	seenURLs := map[string]bool{}
	appendBooks := func(scope *goquery.Selection) {
		scope.Find(rule.BookList.Item).Each(func(_ int, s *goquery.Selection) {
			a := s.Find("a[href]").First()
			href, ok := a.Attr("href")
			if !ok {
				return
			}
			link, err := url.Parse(baseURL)
			if err != nil {
				return
			}
			target, err := url.Parse(href)
			if err != nil {
				return
			}
			resolvedURL := link.ResolveReference(target).String()
			if baseHost := link.Hostname(); baseHost != "" {
				resolved, parseErr := url.Parse(resolvedURL)
				if parseErr != nil || !sameAuthorizedHost(resolved.Hostname(), baseHost) {
					return
				}
			}
			if seenURLs[resolvedURL] {
				return
			}
			seenURLs[resolvedURL] = true
			title := strings.TrimSpace(s.Find(rule.BookList.Title).First().Text())
			if title == "" {
				title = strings.TrimSpace(a.Text())
			}
			if title == "" {
				return
			}
			author := selectorText(s, rule.BookList.Author, "未知作者")
			category := selectorText(s, rule.BookList.Category, defaultCategory)
			latestSelector := s.Find(rule.BookList.LatestChapterNo).First()
			latestText := strings.TrimSpace(latestSelector.Text())
			latest := chapterNumber(latestText, 0)
			if latestHref, ok := latestSelector.Attr("href"); ok {
				latest = chapterNumberFromURL(latestHref, latestText, latest)
			}
			result = append(result, map[string]any{"sourceWorkUrl": resolvedURL, "sourceWorkTitle": title, "authorName": author, "categoryName": category, "serialStatus": normalizeSerialStatus(selectorText(s, rule.BookList.SerialStatus, "")), "remoteLatestChapterNo": latest, "remoteChapterCount": latest})
		})
	}
	if rule.BookList.Group == "" {
		appendBooks(doc.Selection)
	} else {
		doc.Find(rule.BookList.Group).Each(func(_ int, group *goquery.Selection) {
			titleSelector := rule.BookList.GroupTitle
			if titleSelector == "" {
				titleSelector = "h1,h2,h3"
			}
			groupTitle := strings.TrimSpace(group.Find(titleSelector).First().Text())
			if defaultCategory == "" || strings.Contains(groupTitle, defaultCategory) || strings.Contains(defaultCategory, groupTitle) {
				appendBooks(group)
			}
		})
	}
	return result
}

func (w *worker) enrichBook(t *task, rule *selectors, book map[string]any) error {
	workURL, _ := book["sourceWorkUrl"].(string)
	body, _, err := w.fetch(t, workURL)
	if err != nil {
		return err
	}
	for key, value := range extractWorkMetadata(body, rule) {
		book[key] = value
	}
	chapters, err := extractCatalog(body, rule.Catalog.Item, rule.Catalog.Title, workURL, rule.Catalog.ChapterNoFromURL)
	if err != nil {
		return err
	}
	latest := len(chapters)
	for _, item := range chapters {
		if item.ChapterNo > latest {
			latest = item.ChapterNo
		}
	}
	if latest > 0 {
		book["remoteLatestChapterNo"] = latest
		book["remoteChapterCount"] = latest
	}
	politeDelay(t)
	return nil
}

func extractWorkMetadata(raw string, rule *selectors) map[string]any {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil
	}
	result := map[string]any{}
	if value := selectorValue(doc.Selection, rule.Detail.Author, rule.Detail.AuthorAttr); value != "" {
		result["authorName"] = value
	}
	if value := selectorValue(doc.Selection, rule.Detail.Category, rule.Detail.CategoryAttr); value != "" {
		result["categoryName"] = value
	}
	if value := normalizeSerialStatus(selectorValue(doc.Selection, rule.Detail.SerialStatus, rule.Detail.SerialStatusAttr)); value != "" {
		result["serialStatus"] = value
	}
	return result
}

func selectorValue(scope *goquery.Selection, selector, attr string) string {
	if selector == "" {
		return ""
	}
	match := scope.Find(selector).First()
	if match.Length() == 0 {
		return ""
	}
	if attr != "" {
		value, _ := match.Attr(attr)
		return strings.TrimSpace(value)
	}
	return strings.TrimSpace(match.Text())
}

func normalizeSerialStatus(value string) string {
	value = strings.ToUpper(strings.TrimSpace(value))
	for _, token := range []string{"FINISHED", "COMPLETED", "完结", "全本", "大结局"} {
		if strings.Contains(value, token) {
			return "FINISHED"
		}
	}
	for _, token := range []string{"ONGOING", "SERIAL", "连载"} {
		if strings.Contains(value, token) {
			return "ONGOING"
		}
	}
	return ""
}

func selectorText(s *goquery.Selection, selector, fallback string) string {
	if selector == "" {
		return fallback
	}
	value := strings.TrimSpace(s.Find(selector).First().Text())
	if value == "" {
		return fallback
	}
	return value
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
		// Waiting for the shared permit is not an HTTP retry. A conservative
		// rate policy may deny a permit several times before the request is sent.
		for {
			var permit map[string]any
			if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/permit", t.RunID), map[string]any{"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID}, &permit); err != nil {
				return "", 0, err
			}
			if allowed, _ := permit["allowed"].(bool); allowed {
				break
			}
			if strings.Contains(fmt.Sprint(permit["reason"]), "每日请求上限") {
				return "", 0, errors.New(fmt.Sprint(permit["reason"]))
			}
			delay := number(permit["retryAfterMs"], 1000)
			time.Sleep(time.Duration(min(delay, 300000)) * time.Millisecond)
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
		return decodeSourceBody(body, resp.Header.Get("Content-Type")), resp.StatusCode, nil
	}
	return "", 0, errors.New("request retry exhausted")
}

func (w *worker) fetchFormWithRetry(t *task, target string, params map[string]string) (string, error) {
	form := url.Values{}
	for key, value := range params {
		form.Set(key, value)
	}
	for attempt := 0; attempt <= min(t.MaxRetries, 5); attempt++ {
		for {
			var permit map[string]any
			if err := w.post(fmt.Sprintf("/reader/worker/source/runs/%d/permit", t.RunID), map[string]any{"runId": t.RunID, "runToken": t.RunToken, "workerId": w.workerID}, &permit); err != nil {
				return "", err
			}
			if allowed, _ := permit["allowed"].(bool); allowed {
				break
			}
			if strings.Contains(fmt.Sprint(permit["reason"]), "每日请求上限") {
				return "", errors.New(fmt.Sprint(permit["reason"]))
			}
			time.Sleep(time.Duration(min(number(permit["retryAfterMs"], 1000), 300000)) * time.Millisecond)
		}
		req, err := http.NewRequest(http.MethodPost, target, strings.NewReader(form.Encode()))
		if err != nil {
			return "", err
		}
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
		req.Header.Set("User-Agent", "reader-source-worker/1.0 (+authorized-fetch)")
		resp, err := w.client.Do(req)
		if err != nil {
			if attempt < min(t.MaxRetries, 5) {
				time.Sleep(time.Duration(1<<attempt) * time.Second)
				continue
			}
			return "", err
		}
		body, readErr := io.ReadAll(io.LimitReader(resp.Body, 8*1024*1024+1))
		resp.Body.Close()
		if readErr != nil {
			return "", readErr
		}
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			httpErr := sourceHTTPError{status: resp.StatusCode, retryAfter: resp.Header.Get("Retry-After"), message: fmt.Sprintf("source returned HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body[:min(len(body), 512)])))}
			if resp.StatusCode == 401 || resp.StatusCode == 403 || resp.StatusCode == 429 || attempt >= min(t.MaxRetries, 5) {
				return "", httpErr
			}
			time.Sleep(time.Duration(1<<attempt) * time.Second)
			continue
		}
		if len(body) > 8*1024*1024 {
			return "", errors.New("source response exceeds size limit")
		}
		return decodeSourceBody(body, resp.Header.Get("Content-Type")), nil
	}
	return "", errors.New("request retry exhausted")
}

func decodeSourceBody(body []byte, contentType string) string {
	lowerType := strings.ToLower(contentType)
	lowerBody := strings.ToLower(string(body))
	if !strings.Contains(lowerType, "charset=gbk") &&
		!strings.Contains(lowerType, "charset=gb18030") &&
		!strings.Contains(lowerType, "charset=gb2312") &&
		!strings.Contains(lowerBody, "charset=gbk") &&
		!strings.Contains(lowerBody, "charset=gb18030") &&
		!strings.Contains(lowerBody, "charset=gb2312") {
		return string(body)
	}
	command := exec.Command("iconv", "-f", "GB18030", "-t", "UTF-8")
	command.Stdin = bytes.NewReader(body)
	decoded, err := command.Output()
	if err != nil {
		return string(body)
	}
	return string(decoded)
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

func extractCatalog(raw, itemSelector, titleSelector, baseURL string, chapterNoFromURL bool) ([]chapter, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil, err
	}
	result := make([]chapter, 0)
	seenURLs := make(map[string]struct{})
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
		if _, exists := seenURLs[resolved.String()]; exists {
			return
		}
		seenURLs[resolved.String()] = struct{}{}
		title := strings.TrimSpace(selection.Find(titleSelector).First().Text())
		if title == "" {
			title = strings.TrimSpace(selection.Find("a").First().Text())
		}
		chapterName := title
		if chapterNoFromURL {
			title = ""
		}
		result = append(result, chapter{SourceChapterID: resolved.String(), URL: resolved.String(), ChapterNo: chapterNumberFromURL(resolved.String(), title, index+1), ChapterName: chapterName})
	})
	sort.SliceStable(result, func(i, j int) bool { return result[i].ChapterNo < result[j].ChapterNo })
	return result, nil
}

func extractChapter(raw, titleSelector, contentSelector string, c chapter, sourceURL string) (map[string]any, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(raw))
	if err != nil {
		return nil, err
	}
	content := normalizeChapterText(selectionText(doc.Find(contentSelector).First()))
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

func (w *worker) fetchChapter(t *task, rule *selectors, c chapter, sourceURL string) (map[string]any, error) {
	currentURL := sourceURL
	seen := map[string]bool{}
	maxPages := rule.Chapter.MaxPages
	if maxPages <= 0 {
		maxPages = 10
	}
	maxPages = min(maxPages, 20)
	parts := make([]string, 0, maxPages)
	var item map[string]any
	nextChapterURL := ""
	for page := 0; page < maxPages; page++ {
		if seen[currentURL] {
			return nil, errors.New("chapter pagination contains a cycle")
		}
		seen[currentURL] = true
		body, _, err := w.fetch(t, currentURL)
		if err != nil {
			return nil, err
		}
		nextURL := ""
		if rule.Chapter.NextPage != "" || rule.Chapter.NextChapter != "" {
			doc, parseErr := goquery.NewDocumentFromReader(strings.NewReader(body))
			if parseErr != nil {
				return nil, parseErr
			}
			if rule.Chapter.NextPage != "" {
				next := doc.Find(rule.Chapter.NextPage).First()
				if href, exists := next.Attr("href"); exists && strings.TrimSpace(href) != "" {
					base, _ := url.Parse(currentURL)
					target, parseErr := url.Parse(href)
					if parseErr != nil {
						return nil, parseErr
					}
					nextURL = base.ResolveReference(target).String()
					next.Remove()
				}
			}
			if nextURL == "" && rule.Chapter.NextChapter != "" {
				next := doc.Find(rule.Chapter.NextChapter).First()
				href, exists := next.Attr("href")
				if exists && strings.TrimSpace(href) != "" && (rule.Chapter.NextChapterText == "" || strings.Contains(strings.TrimSpace(next.Text()), rule.Chapter.NextChapterText)) {
					base, _ := url.Parse(currentURL)
					target, parseErr := url.Parse(href)
					if parseErr != nil {
						return nil, parseErr
					}
					nextChapterURL = base.ResolveReference(target).String()
					if err := validatePublicSameHost(nextChapterURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
						return nil, err
					}
				}
			}
			doc.Find("script,style").Remove()
			body, _ = doc.Html()
		}
		item, err = extractChapter(body, rule.Chapter.Title, rule.Chapter.Content, c, sourceURL)
		if err != nil {
			return nil, err
		}
		parts = append(parts, item["content"].(string))
		if nextURL == "" {
			break
		}
		if page+1 >= maxPages {
			return nil, errors.New("chapter pagination exceeds maxPages")
		}
		if err := validatePublicSameHost(nextURL, t.SourceWorkURL, w.allowPrivateForTest); err != nil {
			return nil, err
		}
		politeDelay(t)
		currentURL = nextURL
	}
	content := normalizeChapterText(strings.Join(parts, "\n\n"))
	if c.DiscoveredNext {
		actualTitle := strings.TrimSpace(regexp.MustCompile(`[（(]\s*\d+\s*/\s*\d+\s*[）)]\s*$`).ReplaceAllString(fmt.Sprint(item["chapterName"]), ""))
		if actualTitle == "" {
			actualTitle = fmt.Sprintf("第%d章", c.ChapterNo)
		}
		item["chapterName"] = actualTitle
		item["chapterNo"] = chapterNumber(actualTitle, c.ChapterNo)
	} else {
		item["chapterName"] = c.ChapterName
	}
	item["content"] = content
	digest := sha256.Sum256([]byte(content))
	item["contentHash"] = "sha256:" + hex.EncodeToString(digest[:])
	if nextChapterURL != "" {
		item["_nextChapterUrl"] = nextChapterURL
	}
	return item, nil
}

func insertDiscoveredNextChapter(chapters []chapter, index int, item map[string]any, t *task) []chapter {
	raw, ok := item["_nextChapterUrl"]
	delete(item, "_nextChapterUrl")
	if !ok || strings.TrimSpace(fmt.Sprint(raw)) == "" {
		return chapters
	}
	nextURL := strings.TrimSpace(fmt.Sprint(raw))
	for _, existing := range chapters {
		if existing.URL == nextURL {
			return chapters
		}
	}
	currentNo, ok := item["chapterNo"].(int)
	if !ok {
		currentNo = chapters[index].ChapterNo
	}
	nextNo := currentNo + 1
	if t.EndChapterNo != nil && nextNo > *t.EndChapterNo {
		return chapters
	}
	discovered := chapter{SourceChapterID: nextURL, URL: nextURL, ChapterNo: nextNo, DiscoveredNext: true}
	chapters = append(chapters, chapter{})
	copy(chapters[index+2:], chapters[index+1:])
	chapters[index+1] = discovered
	return chapters
}

func selectionText(selection *goquery.Selection) string {
	var builder strings.Builder
	for _, node := range selection.Nodes {
		appendNodeText(node, &builder)
	}
	return builder.String()
}

func appendNodeText(node *html.Node, builder *strings.Builder) {
	if node.Type == html.TextNode {
		builder.WriteString(node.Data)
		return
	}
	name := strings.ToLower(node.Data)
	if name == "br" {
		builder.WriteByte('\n')
		return
	}
	block := name == "p" || name == "div" || name == "li" || name == "section" || name == "article"
	if block && builder.Len() > 0 {
		builder.WriteByte('\n')
	}
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		appendNodeText(child, builder)
	}
	if block {
		builder.WriteByte('\n')
	}
}

func normalizeChapterText(source string) string {
	source = strings.ReplaceAll(source, "\r\n", "\n")
	source = strings.ReplaceAll(source, "\r", "\n")
	source = strings.ReplaceAll(source, "\u00a0", " ")
	multiSpace := regexp.MustCompile(`(?:[ \t\f]{2,}|　{2,})`)
	source = multiSpace.ReplaceAllString(source, "\n\n")
	lines := strings.FieldsFunc(source, func(r rune) bool { return r == '\n' })
	paragraphs := make([]string, 0, len(lines))
	for _, line := range lines {
		if value := strings.TrimSpace(line); value != "" {
			paragraphs = append(paragraphs, value)
		}
	}
	return strings.Join(paragraphs, "\n\n")
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
	value := strings.NewReplacer("{url}", workURL, "{workUrl}", workURL, "{workId}", id, "{chapterUrl}", chapterURL,
		"{id}", chapterID, "{chapterId}", chapterID, "{chapterNo}", strconv.Itoa(chapterNo)).Replace(template)
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
	if err != nil || !sameAuthorizedHost(got.Hostname(), allowed.Hostname()) {
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

func sameAuthorizedHost(actual, expected string) bool {
	actual = strings.TrimSuffix(strings.ToLower(actual), ".")
	expected = strings.TrimSuffix(strings.ToLower(expected), ".")
	return actual == expected || strings.TrimPrefix(actual, "www.") == strings.TrimPrefix(expected, "www.")
}

func politeDelay(t *task) {
	if t.MaxDelayMs < t.MinDelayMs {
		t.MaxDelayMs = t.MinDelayMs
	}
	if t.MaxDelayMs > 0 {
		time.Sleep(time.Duration(t.MinDelayMs) * time.Millisecond)
	}
}

var chapterPattern = regexp.MustCompile(`第\s*([0-9零〇一二两三四五六七八九十百千万亿]+)\s*(?:章|节|回|集)`)

func chapterNumber(title string, fallback int) int {
	match := chapterPattern.FindStringSubmatch(title)
	if len(match) == 2 {
		if value, err := strconv.Atoi(match[1]); err == nil {
			return value
		}
		return chineseNumber(match[1], fallback)
	}
	return fallback
}

func chineseNumber(value string, fallback int) int {
	digits := map[rune]int{'零': 0, '〇': 0, '一': 1, '二': 2, '两': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9}
	units := map[rune]int{'十': 10, '百': 100, '千': 1000, '万': 10000, '亿': 100000000}
	total, section, number := 0, 0, 0
	for _, current := range value {
		if digit, ok := digits[current]; ok {
			number = digit
			continue
		}
		unit, ok := units[current]
		if !ok {
			return fallback
		}
		if unit < 10000 {
			if number == 0 {
				number = 1
			}
			section += number * unit
		} else {
			total += (section + number) * unit
			section, number = 0, 0
		}
	}
	return total + section + number
}

func chapterNumberFromURL(rawURL, title string, fallback int) int {
	if value := chapterNumber(title, 0); value > 0 {
		return value
	}
	parsed, err := url.Parse(rawURL)
	if err == nil {
		parts := strings.Split(strings.Trim(parsed.Path, "/"), "/")
		if len(parts) > 0 {
			last := parts[len(parts)-1]
			if strings.HasSuffix(strings.ToLower(last), ".html") {
				last = last[:len(last)-len(".html")]
			}
			if value, parseErr := strconv.Atoi(last); parseErr == nil && value > 0 {
				return value
			}
		}
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
