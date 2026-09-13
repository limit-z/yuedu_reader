package main

import "testing"

func TestExtractChapterPreservesParagraphs(t *testing.T) {
	chapterData, err := extractChapter(`<h1>第1章</h1><div id="content">第一段。<br><br>第二段。<p>第三段。</p></div>`, "h1", "#content", chapter{ChapterName: "第1章", ChapterNo: 1}, "https://example.com/1.html")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := chapterData["content"], "第一段。\n\n第二段。\n\n第三段。"; got != want {
		t.Fatalf("content = %q, want %q", got, want)
	}
}

func TestNormalizeChapterTextHandlesFullWidthSpacing(t *testing.T) {
	if got, want := normalizeChapterText("第一段。　　第二段。"), "第一段。\n\n第二段。"; got != want {
		t.Fatalf("content = %q, want %q", got, want)
	}
}

func TestExtractBooksFiltersRankingGroupAndReadsStatus(t *testing.T) {
	var rule selectors
	rule.BookList.Group = ".box"
	rule.BookList.GroupTitle = "h3"
	rule.BookList.Item = "li"
	rule.BookList.Title = "a"
	books := extractBooks(`<div class="box"><h3>玄幻排行榜</h3><li><a href="/1/">甲</a></li></div><div class="box"><h3>言情排行榜</h3><li><a href="/2/">乙</a></li></div>`, &rule, "https://example.com/rank", "言情")
	if len(books) != 1 || books[0]["sourceWorkTitle"] != "乙" {
		t.Fatalf("books = %#v", books)
	}

	rule.Detail.Author = "meta[name=author]"
	rule.Detail.AuthorAttr = "content"
	rule.Detail.SerialStatus = "meta[property='og:novel:status']"
	rule.Detail.SerialStatusAttr = "content"
	metadata := extractWorkMetadata(`<meta name="author" content="作者乙"><meta property="og:novel:status" content="已完结">`, &rule)
	if metadata["authorName"] != "作者乙" || metadata["serialStatus"] != "FINISHED" {
		t.Fatalf("metadata = %#v", metadata)
	}
}

func TestExtractCatalogDeduplicatesAndSortsAllChapters(t *testing.T) {
	chapters, err := extractCatalog(`<div id="list"><dd><a href="/book/3.html">第三章</a></dd><dd><a href="/book/1.html">第一章</a></dd><dd><a href="/book/3.html">第三章</a></dd><dd><a href="/book/2.html">第二章</a></dd></div>`, "#list dd", "a", "https://example.com/book/", false)
	if err != nil {
		t.Fatal(err)
	}
	if len(chapters) != 3 || chapters[0].ChapterNo != 1 || chapters[1].ChapterNo != 2 || chapters[2].ChapterNo != 3 {
		t.Fatalf("chapters = %#v", chapters)
	}
}

func TestExtractCatalogCanPreferURLNumberOverBrokenTitle(t *testing.T) {
	chapters, err := extractCatalog(`<dd><a href="/book/14.html">第十五章 红绳</a></dd>`, "dd", "a", "https://example.com/book/", true)
	if err != nil {
		t.Fatal(err)
	}
	if len(chapters) != 1 || chapters[0].ChapterNo != 14 {
		t.Fatalf("chapters = %#v", chapters)
	}
}

func TestNormalizeSerialStatusRecognizesFinalChapter(t *testing.T) {
	if got := normalizeSerialStatus("最新章节：大结局"); got != "FINISHED" {
		t.Fatalf("status = %q", got)
	}
}

func TestChapterNumberPrefersTitleOverInternalURLID(t *testing.T) {
	if got := chapterNumberFromURL("https://example.com/88171548.html", "第001章 开始", 9); got != 1 {
		t.Fatalf("chapter number = %d", got)
	}
}

func TestChapterNumberParsesChineseTitleAndIgnoresSuffix(t *testing.T) {
	if got := chapterNumberFromURL("https://example.com/internal.html", "第四十一章 交织的阴谋（1）", 9); got != 41 {
		t.Fatalf("chapter number = %d", got)
	}
}

func TestExtractBooksDeduplicatesSameWorkAcrossSections(t *testing.T) {
	var rule selectors
	rule.BookList.Item = ".item"
	rule.BookList.Title = "a"
	books := extractBooks(`<div class="item"><a href="/1/">甲</a></div><li class="item"><a href="/1/">甲</a></li>`, &rule, "https://example.com/", "玄幻")
	if len(books) != 1 {
		t.Fatalf("books = %#v", books)
	}
}

func TestExtractBooksIgnoresExternalRecommendations(t *testing.T) {
	var rule selectors
	rule.BookList.Item = ".item"
	rule.BookList.Title = "a"
	books := extractBooks(`<div class="item"><a href="/1/">甲</a></div><div class="item"><a href="https://partner.example/2/">乙</a></div>`, &rule, "https://example.com/", "玄幻")
	if len(books) != 1 || books[0]["sourceWorkTitle"] != "甲" {
		t.Fatalf("books = %#v", books)
	}
}

func TestSelectRangeKeepsEveryChapterWhenEndIsUnset(t *testing.T) {
	chapters := []chapter{
		{ChapterNo: 1}, {ChapterNo: 2}, {ChapterNo: 3}, {ChapterNo: 4}, {ChapterNo: 5},
	}
	selected := selectRange(chapters, &task{StartChapterNo: 1})
	if len(selected) != 5 || selected[4].ChapterNo != 5 {
		t.Fatalf("selected = %#v", selected)
	}
}

func TestInsertDiscoveredNextChapterFillsCatalogGap(t *testing.T) {
	chapters := []chapter{{URL: "https://example.com/4.html", ChapterNo: 4}, {URL: "https://example.com/6.html", ChapterNo: 6}}
	item := map[string]any{"chapterNo": 4, "_nextChapterUrl": "https://example.com/5.html"}
	chapters = insertDiscoveredNextChapter(chapters, 0, item, &task{})
	if len(chapters) != 3 || chapters[1].ChapterNo != 5 || chapters[1].URL != "https://example.com/5.html" || !chapters[1].DiscoveredNext {
		t.Fatalf("chapters = %#v", chapters)
	}
	if _, exists := item["_nextChapterUrl"]; exists {
		t.Fatal("internal next chapter URL must not be submitted")
	}
}
