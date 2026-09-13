import unittest

from chapter_text import normalize_chapter_text
from reader_source_worker import Worker


class ReaderSourceWorkerTextTest(unittest.TestCase):
    def test_normalize_chapter_text_preserves_paragraphs(self):
        source = "第一段。    第二段。\n\n第三段。"
        self.assertEqual("第一段。\n\n第二段。\n\n第三段。", normalize_chapter_text(source))

    def test_normalize_chapter_text_handles_full_width_spacing(self):
        self.assertEqual("第一段。\n\n第二段。", normalize_chapter_text("第一段。　　第二段。"))

    def test_extracts_requested_ranking_group_and_detail_metadata(self):
        worker = Worker.__new__(Worker)
        html = '<div class="box"><h3>玄幻推荐排行榜</h3><li>1<a href="/1/">甲</a></li></div><div class="box"><h3>言情推荐排行榜</h3><li>1<a href="/2/">乙</a></li></div>'
        rule = {"group": ".box", "groupTitle": "h3", "item": "li", "title": "a"}
        books = worker._extract_books(html, rule, "https://example.com/rank", "言情")
        self.assertEqual(["乙"], [item["sourceWorkTitle"] for item in books])

        detail = '<meta name="author" content="作者乙"><meta property="og:novel:category" content="言情小说"><meta property="og:novel:status" content="已完结">'
        metadata = worker._extract_work_metadata(detail, {
            "author": "meta[name=author]", "authorAttr": "content",
            "category": "meta[property='og:novel:category']", "categoryAttr": "content",
            "serialStatus": "meta[property='og:novel:status']", "serialStatusAttr": "content",
        })
        self.assertEqual({"authorName": "作者乙", "categoryName": "言情小说", "serialStatus": "FINISHED"}, metadata)

    def test_catalog_deduplicates_latest_preview_and_sorts_all_chapters(self):
        worker = Worker.__new__(Worker)
        html = '<div id="list"><dd><a href="/book/3.html">第三章</a></dd><dd><a href="/book/1.html">第一章</a></dd><dd><a href="/book/3.html">第三章</a></dd><dd><a href="/book/2.html">第二章</a></dd></div>'
        chapters = worker._extract_catalog(html, {"item": "#list dd", "title": "a"}, "https://example.com/book/")
        self.assertEqual([1, 2, 3], [item["chapterNo"] for item in chapters])
        self.assertEqual(3, len(chapters))

    def test_fetch_chapter_merges_declared_pagination(self):
        worker = Worker.__new__(Worker)
        worker._polite_delay = lambda task: None
        worker._validate_public_same_host = lambda url, source: None
        pages = {
            "https://example.com/1.html": '<h1>第一章（1 / 2）</h1><div id="content"><p>第一段。</p><a rel="next" href="1_2.html">下一页</a></div>',
            "https://example.com/1_2.html": '<h1>第一章（2 / 2）</h1><div id="content"><p>第二段。</p></div>',
        }
        worker._fetch_with_retry = lambda task, url: pages[url]
        item = worker._fetch_chapter(
            {"sourceWorkUrl": "https://example.com/book/"},
            {"title": "h1", "content": "#content", "nextPage": "#content a[rel=next]"},
            {"sourceChapterId": "1", "chapterName": "第一章", "chapterNo": 1},
            "https://example.com/1.html",
        )
        self.assertEqual("第一段。\n\n第二段。", item["content"])
        self.assertEqual("第一章", item["chapterName"])

    def test_fetch_chapter_exposes_declared_same_host_next_chapter(self):
        worker = Worker.__new__(Worker)
        worker._polite_delay = lambda task: None
        worker._validate_public_same_host = lambda url, source: None
        worker._fetch_with_retry = lambda task, url: (
            '<h1>第4章（3 / 3）</h1><div id="content"><p>正文。</p></div>'
            '<a id="next_url" href="/book/5.html">下一章</a>'
        )
        item = worker._fetch_chapter(
            {"sourceWorkUrl": "https://example.com/book/"},
            {"title": "h1", "content": "#content", "nextPage": "#content a[rel=next]",
             "nextChapter": "#next_url", "nextChapterText": "下一章"},
            {"sourceChapterId": "4", "chapterName": "第4章", "chapterNo": 4},
            "https://example.com/book/4.html",
        )
        self.assertEqual("https://example.com/book/5.html", item["_nextChapterUrl"])

    def test_inserts_chapter_missing_from_catalog_from_next_link(self):
        worker = Worker.__new__(Worker)
        chapters = [
            {"url": "https://example.com/4.html", "chapterNo": 4},
            {"url": "https://example.com/6.html", "chapterNo": 6},
        ]
        item = {"chapterNo": 4, "_nextChapterUrl": "https://example.com/5.html"}
        worker._insert_discovered_next_chapter(chapters, 0, item, {"endChapterNo": None})
        self.assertEqual([4, 5, 6], [chapter["chapterNo"] for chapter in chapters])
        self.assertNotIn("_nextChapterUrl", item)

    def test_catalog_prefers_chapter_number_in_title_over_internal_url_id(self):
        worker = Worker.__new__(Worker)
        html = '<ul><li><a href="/book/88171548.html">第001章 开始</a></li></ul>'
        chapters = worker._extract_catalog(html, {"item": "li", "title": "a"}, "https://example.com/book/")
        self.assertEqual(1, chapters[0]["chapterNo"])

    def test_catalog_does_not_use_parenthesized_suffix_as_chapter_number(self):
        worker = Worker.__new__(Worker)
        html = '<ul><li><a href="/book/88171548.html">第四十一章 交织的阴谋（1）</a></li></ul>'
        chapters = worker._extract_catalog(html, {"item": "li", "title": "a"}, "https://example.com/book/")
        self.assertEqual(41, chapters[0]["chapterNo"])

    def test_catalog_parses_chinese_chapter_number(self):
        worker = Worker.__new__(Worker)
        chapters = worker._extract_catalog(
            '<ul><li><a href="/book/internal.html">第四十一章 交织的阴谋（1）</a></li></ul>',
            {"item": "li", "title": "a"}, "https://example.com/book/"
        )
        self.assertEqual(41, chapters[0]["chapterNo"])

    def test_catalog_can_prefer_url_number_over_broken_title(self):
        worker = Worker.__new__(Worker)
        chapters = worker._extract_catalog(
            '<dd><a href="/book/14.html">第十五章 红绳</a></dd>',
            {"item": "dd", "title": "a", "chapterNoFromUrl": True},
            "https://example.com/book/"
        )
        self.assertEqual(14, chapters[0]["chapterNo"])

    def test_book_discovery_deduplicates_same_work_across_sections(self):
        worker = Worker.__new__(Worker)
        html = '<div class="item"><a href="/1/">甲</a></div><li class="item"><a href="/1/">甲</a></li>'
        books = worker._extract_books(html, {"item": ".item", "title": "a"}, "https://example.com/", "玄幻")
        self.assertEqual(1, len(books))

    def test_book_discovery_ignores_external_recommendations(self):
        worker = Worker.__new__(Worker)
        html = '<div class="item"><a href="/1/">甲</a></div><div class="item"><a href="https://partner.example/2/">乙</a></div>'
        books = worker._extract_books(html, {"item": ".item", "title": "a"}, "https://example.com/", "玄幻")
        self.assertEqual(["甲"], [book["sourceWorkTitle"] for book in books])

    def test_open_ended_range_keeps_every_catalog_chapter(self):
        worker = Worker.__new__(Worker)
        chapters = [{"chapterNo": number} for number in range(1, 6)]
        selected = worker._select_range(chapters, {"startChapterNo": 1, "endChapterNo": None})
        self.assertEqual([1, 2, 3, 4, 5], [item["chapterNo"] for item in selected])


if __name__ == "__main__":
    unittest.main()
