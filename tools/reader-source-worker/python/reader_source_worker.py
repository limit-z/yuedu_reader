#!/usr/bin/env python3
"""Reference Python worker for authorized, rate-limited public sources."""

import datetime as dt
import hashlib
import ipaddress
import json
import logging
import os
import random
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from threading import Event, Thread

from bs4 import BeautifulSoup

from chapter_text import normalize_chapter_text


LOG = logging.getLogger("reader-source-worker")
CHAPTER_BATCH_SIZE = 20
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
RATE_LIMIT_WAIT_MAX_SECONDS = 75


class SourceHttpError(Exception):
    def __init__(self, status, message, retry_after=None):
        super().__init__(message)
        self.status = status
        self.retry_after = retry_after


class Worker:
    def __init__(self):
        self.api_base = os.getenv("READER_SOURCE_API_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
        self.secret = os.getenv("READER_SOURCE_WORKER_SECRET", "")
        self.worker_id = os.getenv("READER_SOURCE_WORKER_ID", socket.gethostname() + "-worker")
        self.poll_seconds = max(2, int(os.getenv("READER_SOURCE_POLL_SECONDS", "5")))
        self.max_parallel = max(1, min(8, int(os.getenv("READER_SOURCE_MAX_PARALLEL", "3"))))
        self.allow_private_for_test = os.getenv("READER_SOURCE_ALLOW_PRIVATE_FOR_TEST", "false").lower() == "true"
        self.stop_event = Event()
        if not self.secret:
            raise RuntimeError("READER_SOURCE_WORKER_SECRET is required")

    def run_forever(self):
        futures = set()
        with ThreadPoolExecutor(max_workers=self.max_parallel, thread_name_prefix="reader-source") as pool:
            while not self.stop_event.is_set():
                done = {future for future in futures if future.done()}
                futures.difference_update(done)
                if len(futures) >= self.max_parallel:
                    wait(futures, timeout=self.poll_seconds, return_when=FIRST_COMPLETED)
                    continue
                try:
                    task = self._post("/reader/worker/source/runs/claim", {
                        "executorType": "PYTHON",
                        "workerId": self.worker_id,
                    })
                except Exception as exc:
                    LOG.warning("claim request failed: %s", exc)
                    self.stop_event.wait(self.poll_seconds)
                    continue
                if not task:
                    self.stop_event.wait(self.poll_seconds)
                    continue
                LOG.info("claimed run=%s task=%s", task.get("runId"), task.get("taskId"))
                futures.add(pool.submit(self._run_task, task))

    def _run_task(self, task):
        heartbeat_stop = Event()
        heartbeat = Thread(target=self._heartbeat_loop, args=(task, heartbeat_stop), daemon=True)
        heartbeat.start()
        try:
            selectors = self._load_selectors(task.get("selectorJson"))
            if task.get("collectionMode") in ("ALL", "CATEGORY"):
                self._run_batch_task(task, selectors)
                return
            source_work_url = self._resolve_source_work_url(task, selectors)
            catalog_url = self._expand_url(task.get("catalogUrlTemplate") or source_work_url, task, None)
            self._validate_public_same_host(catalog_url, source_work_url)
            catalog_html = self._fetch_with_retry(task, catalog_url)
            chapters = self._extract_catalog(catalog_html, selectors["catalog"], catalog_url)
            chapters = self._select_range(chapters, task)
            if not chapters:
                raise ValueError("catalog contains no chapters in the requested range")

            metrics = {"requests": 1, "success": 0, "skipped": 0, "failures": 0, "tooManyRequests": 0}
            for offset in range(0, len(chapters), CHAPTER_BATCH_SIZE):
                batch = chapters[offset:offset + CHAPTER_BATCH_SIZE]
                items = []
                for chapter in batch:
                    chapter_url = self._expand_url(task.get("chapterUrlTemplate") or chapter["url"], task, chapter)
                    self._validate_public_same_host(chapter_url, task["sourceWorkUrl"])
                    try:
                        metrics["requests"] += 1
                        item = self._fetch_chapter(task, selectors["chapter"], chapter, chapter_url)
                        items.append(item)
                        metrics["success"] += 1
                    except SourceHttpError as exc:
                        if exc.status == 429:
                            metrics["tooManyRequests"] += 1
                        self._report_error(task, exc, chapter_url)
                        return
                    except Exception as exc:
                        metrics["failures"] += 1
                        self._report_error(task, SourceHttpError(None, str(exc)), chapter_url)
                        return
                    self._polite_delay(task)

                cursor = batch[-1]["chapterNo"]
                completed = offset + CHAPTER_BATCH_SIZE >= len(chapters)
                self._post("/reader/worker/source/runs/{}/result".format(task["runId"]), {
                    "runId": task["runId"],
                    "runToken": task["runToken"],
                    "workerId": self.worker_id,
                    "executorType": "PYTHON",
                    "batchId": "py-{}-{}".format(task["runId"], uuid.uuid4().hex),
                    "ruleVersion": task["ruleVersion"],
                    "cursorChapterNo": cursor,
                    "completed": completed,
                    "items": items,
                    "metrics": metrics,
                })
                metrics = {"requests": 0, "success": 0, "skipped": 0, "failures": 0, "tooManyRequests": 0}
        except SourceHttpError as exc:
            self._report_error(task, exc, task.get("sourceWorkUrl"))
        except Exception as exc:
            self._report_error(task, SourceHttpError(None, str(exc)), task.get("sourceWorkUrl"))
        finally:
            heartbeat_stop.set()

    def _run_batch_task(self, task, selectors):
        """Discover books first; the service decides dedupe and incremental ranges."""
        book_list = selectors.get("bookList") or {}
        if not book_list.get("item") or not book_list.get("title"):
            raise ValueError("batch collection requires bookList.item and bookList.title selectors")
        list_url = self._expand_url(task.get("catalogUrlTemplate") or task["sourceWorkUrl"], task, None)
        self._validate_public_same_host(list_url, task["sourceWorkUrl"])
        html = self._fetch_with_retry(task, list_url)
        books = self._extract_books(html, book_list, list_url, task.get("categoryName"))
        limit = max(1, int(task.get("bookLimit") or 1))
        books = books[:limit]
        if not books:
            raise ValueError("book list contains no books")
        discovery_ack = self._post("/reader/worker/source/runs/{}/books".format(task["runId"]), {
            "runId": task["runId"], "runToken": task["runToken"], "workerId": self.worker_id,
            "batchId": "books-{}-{}".format(task["runId"], uuid.uuid4().hex), "books": books, "completed": True,
        })
        if self._is_terminal(discovery_ack):
            return
        while True:
            next_task = self._post("/reader/worker/source/runs/{}/books/claim".format(task["runId"]), {
                "runId": task["runId"], "runToken": task["runToken"], "workerId": self.worker_id,
            })
            if not next_task:
                return
            self._run_single_book(next_task, selectors)

    @staticmethod
    def _is_terminal(ack):
        if not ack:
            return False
        return ack.get("runStatus") in ("COMPLETED", "FAILED", "CANCELED") or ack.get("taskStatus") in ("COMPLETED", "WAITING_REVIEW")

    def _run_single_book(self, task, selectors):
        catalog_url = self._expand_url(task.get("catalogUrlTemplate") or task["sourceWorkUrl"], task, None)
        self._validate_public_same_host(catalog_url, task["sourceWorkUrl"])
        catalog_html = self._fetch_with_retry(task, catalog_url)
        work_metadata = self._extract_work_metadata(catalog_html, selectors.get("detail") or {})
        chapters = self._select_range(self._extract_catalog(catalog_html, selectors["catalog"], catalog_url), task)
        if not chapters:
            raise ValueError("catalog contains no chapters in the requested range")
        offset = 0
        while offset < len(chapters):
            items = []
            while offset < len(chapters) and len(items) < CHAPTER_BATCH_SIZE:
                chapter = chapters[offset]
                chapter_url = self._expand_url(task.get("chapterUrlTemplate") or chapter["url"], task, chapter)
                self._validate_public_same_host(chapter_url, task["sourceWorkUrl"])
                item = self._fetch_chapter(task, selectors["chapter"], chapter, chapter_url)
                self._insert_discovered_next_chapter(chapters, offset, item, task)
                item["taskBookId"] = task.get("taskBookId")
                item.update(work_metadata)
                items.append(item)
                offset += 1
                self._polite_delay(task)
            self._post("/reader/worker/source/runs/{}/result".format(task["runId"]), {
                "runId": task["runId"], "runToken": task["runToken"], "workerId": self.worker_id,
                "executorType": "PYTHON", "batchId": "py-book-{}-{}".format(task["runId"], uuid.uuid4().hex),
                "ruleVersion": task["ruleVersion"], "cursorChapterNo": items[-1]["chapterNo"],
                "completed": offset >= len(chapters), "items": items,
                "metrics": {"requests": len(items) + 1, "success": len(items), "skipped": 0, "failures": 0, "tooManyRequests": 0},
            })

    def _insert_discovered_next_chapter(self, chapters, index, item, task):
        next_url = item.pop("_nextChapterUrl", None)
        if not next_url:
            return
        if any(chapter.get("url") == next_url for chapter in chapters):
            return
        chapter_no = int(item["chapterNo"]) + 1
        end = int(task["endChapterNo"]) if task.get("endChapterNo") else None
        if end is not None and chapter_no > end:
            return
        chapters.insert(index + 1, {
            "sourceChapterId": next_url,
            "url": next_url,
            "chapterNo": chapter_no,
            "chapterName": "",
            "discoveredFromNext": True,
        })

    def _heartbeat_loop(self, task, stop_event):
        interval = max(10, int(task.get("claimLeaseSeconds", 90)) // 3)
        while not stop_event.wait(interval):
            payload = {
                "runId": task["runId"],
                "runToken": task["runToken"],
                "workerId": self.worker_id,
            }
            # A backend restart must not silently abandon a long-running request.
            while not stop_event.is_set():
                try:
                    self._post("/reader/worker/source/runs/{}/heartbeat".format(task["runId"]), payload)
                    break
                except Exception as exc:
                    LOG.warning("heartbeat failed run=%s; retrying: %s", task.get("runId"), exc)
                    stop_event.wait(min(interval, 10))

    def _fetch_with_retry(self, task, url):
        retries = max(0, min(5, int(task.get("maxRetries") or 0)))
        for attempt in range(retries + 1):
            self._wait_for_permit(task)
            try:
                return self._fetch(url, task)
            except SourceHttpError as exc:
                if exc.status in (401, 403, 429) or attempt >= retries:
                    raise
                backoff = min(60, 2 ** attempt)
                time.sleep(backoff)
        raise RuntimeError("request retry exhausted")

    def _fetch(self, url, task):
        request = urllib.request.Request(url, headers={
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "User-Agent": "reader-source-worker/1.0 (+authorized-fetch)",
        })
        try:
            with urllib.request.urlopen(request, timeout=(int(task.get("connectTimeoutMs") or 10000) + int(task.get("readTimeoutMs") or 20000)) / 1000) as response:
                if not (200 <= response.status < 300):
                    raise SourceHttpError(response.status, "source returned HTTP {}".format(response.status))
                body = response.read(MAX_RESPONSE_BYTES + 1)
                if len(body) > MAX_RESPONSE_BYTES:
                    raise SourceHttpError(None, "source response exceeds size limit")
                content_type = response.headers.get("Content-Type", "").lower()
                meta_prefix = body[:8192].lower()
                source_declares_gbk = any(marker in meta_prefix for marker in (b"charset=gbk", b"charset=gb18030", b"charset=gb2312"))
                charset = response.headers.get_content_charset() or (
                    "gb18030" if "gbk" in content_type or "gb2312" in content_type or source_declares_gbk else "utf-8"
                )
                return body.decode(charset, errors="replace")
        except urllib.error.HTTPError as exc:
            retry_after = exc.headers.get("Retry-After")
            raise SourceHttpError(exc.code, "source returned HTTP {}".format(exc.code), retry_after)
        except urllib.error.URLError as exc:
            raise SourceHttpError(None, "source request failed: {}".format(exc.reason))

    def _wait_for_permit(self, task):
        path = "/reader/worker/source/runs/{}/permit".format(task["runId"])
        waited = 0.0
        while True:
            permit = self._post(path, {
                "runId": task["runId"],
                "runToken": task["runToken"],
                "workerId": self.worker_id,
            })
            if permit.get("allowed"):
                return
            reason = str(permit.get("reason") or "")
            if "每日请求上限" in reason:
                raise RuntimeError(reason)
            delay = max(0.1, min(300, float(permit.get("retryAfterMs") or 1000) / 1000))
            if "每分钟请求上限" in reason:
                remaining = RATE_LIMIT_WAIT_MAX_SECONDS - waited
                if remaining <= 0:
                    raise SourceHttpError(None, "RATE_LIMIT_WAIT: {}".format(reason))
                delay = min(delay, remaining)
            LOG.info("permit denied run=%s retryIn=%.1fs reason=%s", task.get("runId"), delay, permit.get("reason"))
            time.sleep(delay)
            waited += delay

    def _report_error(self, task, exc, url):
        status = getattr(exc, "status", None)
        retry_at = None
        retry_after = getattr(exc, "retry_after", None)
        if retry_after and str(retry_after).isdigit():
            # The API field is Java LocalDateTime, so omit the timezone suffix.
            retry_at = (dt.datetime.now() + dt.timedelta(seconds=int(retry_after))).replace(microsecond=0).isoformat()
        try:
            self._post("/reader/worker/source/runs/{}/error".format(task["runId"]), {
                "runId": task["runId"],
                "runToken": task["runToken"],
                "workerId": self.worker_id,
                "errorType": "RATE_LIMIT" if str(exc).startswith("RATE_LIMIT_WAIT:") else ("HTTP" if status else "PARSE"),
                "httpStatus": status,
                "sourceUrl": url,
                "message": str(exc)[:500],
                "retryAt": retry_at,
            })
        except Exception as report_exc:
            LOG.error("failed to report run=%s error: %s", task.get("runId"), report_exc)

    def _post(self, path, payload):
        request = urllib.request.Request(self.api_base + path, data=json.dumps(payload).encode("utf-8"), headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Reader-Worker-Secret": self.secret,
        }, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise RuntimeError("worker API returned HTTP {}".format(exc.code))
        if result.get("code") not in (None, 200):
            raise RuntimeError(result.get("msg") or "worker API request failed")
        return result.get("data")

    def _load_selectors(self, raw):
        selectors = json.loads(raw or "{}")
        catalog = selectors.get("catalog") or {}
        chapter = selectors.get("chapter") or {}
        book_list = selectors.get("bookList") or {}
        detail = selectors.get("detail") or {}
        required = (catalog.get("item"), catalog.get("title"), chapter.get("content"))
        if not all(isinstance(value, str) and value.strip() for value in required):
            raise ValueError("catalog.item, catalog.title and chapter.content CSS selectors are required")
        return {"catalog": catalog, "chapter": chapter, "bookList": book_list, "detail": detail, "search": selectors.get("search") or {}}

    def _extract_catalog(self, html, selectors, base_url):
        soup = BeautifulSoup(html, "html.parser")
        result = []
        seen_urls = set()
        base_host = urllib.parse.urlparse(base_url).hostname
        for index, node in enumerate(soup.select(selectors["item"]), 1):
            anchor = node.select_one("a[href]") or (node if node.name == "a" and node.get("href") else None)
            if not anchor:
                continue
            url = urllib.parse.urljoin(base_url, anchor["href"])
            # Some catalogs append recommended books from other domains. They
            # are not chapters and must never become outbound source requests.
            if base_host and not self._same_authorized_host(urllib.parse.urlparse(url).hostname, base_host):
                continue
            if url in seen_urls:
                continue
            seen_urls.add(url)
            title_node = node.select_one(selectors["title"]) or anchor
            title = self._clean_text(title_node.get_text(" ", strip=True))
            chapter_no = self._chapter_no_from_url(
                url, "" if selectors.get("chapterNoFromUrl") else title, index
            )
            result.append({"sourceChapterId": url, "url": url, "chapterNo": chapter_no, "chapterName": title})
        return sorted(result, key=lambda item: item["chapterNo"])

    def _extract_books(self, html, selectors, base_url, category):
        soup = BeautifulSoup(html, "html.parser")
        result = []
        seen_urls = set()
        base_host = urllib.parse.urlparse(base_url).hostname
        nodes = soup.select(selectors["item"])
        if selectors.get("group"):
            nodes = []
            for group in soup.select(selectors["group"]):
                title_node = group.select_one(selectors.get("groupTitle") or "h1,h2,h3")
                group_title = self._clean_text(title_node.get_text(" ", strip=True)) if title_node else ""
                if category and category not in group_title and group_title not in category:
                    continue
                nodes.extend(group.select(selectors["item"]))
        for node in nodes:
            anchor = node.select_one("a[href]") or (node if node.name == "a" and node.get("href") else None)
            if not anchor:
                continue
            source_work_url = urllib.parse.urljoin(base_url, anchor["href"])
            # Ranking pages may contain partner/recommendation links. They are
            # not source books and must never enter the task-book queue.
            if base_host and not self._same_authorized_host(urllib.parse.urlparse(source_work_url).hostname, base_host):
                continue
            if source_work_url in seen_urls:
                continue
            seen_urls.add(source_work_url)
            title_node = node.select_one(selectors["title"]) or anchor
            author_node = node.select_one(selectors.get("author")) if selectors.get("author") else None
            category_node = node.select_one(selectors.get("category")) if selectors.get("category") else None
            latest_node = node.select_one(selectors.get("latestChapterNo")) if selectors.get("latestChapterNo") else None
            latest_text = self._clean_text(latest_node.get_text(" ", strip=True)) if latest_node else ""
            latest_match = re.search(r"(\d+)", latest_text)
            latest_no = int(latest_match.group(1)) if latest_match else 0
            if latest_node and latest_node.get("href"):
                latest_no = self._chapter_no_from_url(latest_node["href"], latest_text, latest_no)
            result.append({
                "sourceWorkUrl": source_work_url,
                "sourceWorkTitle": self._clean_text(title_node.get_text(" ", strip=True)),
                "authorName": self._clean_text(author_node.get_text(" ", strip=True)) if author_node else "未知作者",
                "categoryName": self._clean_text(category_node.get_text(" ", strip=True)) if category_node else category,
                "remoteLatestChapterNo": latest_no,
                "remoteChapterCount": latest_no,
                "serialStatus": self._normalize_serial_status(self._selector_value(node, selectors, "serialStatus")),
            })
        return result

    def _enrich_book(self, task, selectors, book):
        detail_html = self._fetch_with_retry(task, book["sourceWorkUrl"])
        metadata = self._extract_work_metadata(detail_html, selectors.get("detail") or {})
        chapters = self._extract_catalog(detail_html, selectors["catalog"], book["sourceWorkUrl"])
        chapter_count = max([item["chapterNo"] for item in chapters] + [len(chapters), 0])
        for key in ("authorName", "categoryName", "serialStatus"):
            if metadata.get(key):
                book[key] = metadata[key]
        if chapter_count:
            book["remoteLatestChapterNo"] = chapter_count
            book["remoteChapterCount"] = chapter_count
        self._polite_delay(task)
        return book

    def _extract_work_metadata(self, html, selectors):
        if not selectors:
            return {}
        soup = BeautifulSoup(html, "html.parser")
        result = {
            "authorName": self._selector_value(soup, selectors, "author"),
            "categoryName": self._selector_value(soup, selectors, "category"),
            "serialStatus": self._normalize_serial_status(self._selector_value(soup, selectors, "serialStatus")),
        }
        return {key: value for key, value in result.items() if value}

    def _selector_value(self, node, selectors, key):
        selector = selectors.get(key)
        if not selector:
            return ""
        matched = node.select_one(selector)
        if not matched:
            return ""
        attr = selectors.get(key + "Attr")
        value = matched.get(attr, "") if attr else matched.get_text(" ", strip=True)
        return self._clean_text(value)

    @staticmethod
    def _normalize_serial_status(value):
        normalized = str(value or "").strip().upper()
        if any(token in normalized for token in ("FINISHED", "COMPLETED", "完结", "全本", "大结局")):
            return "FINISHED"
        if any(token in normalized for token in ("ONGOING", "SERIAL", "连载")):
            return "ONGOING"
        return ""

    def _extract_chapter(self, html, selectors, chapter, url):
        soup = BeautifulSoup(html, "html.parser")
        if selectors.get("nextPage"):
            for node in soup.select(selectors["nextPage"]):
                node.decompose()
        for node in soup.select("script,style"):
            node.decompose()
        content = soup.select_one(selectors["content"])
        if not content:
            raise ValueError("chapter content selector matched no element")
        title_node = soup.select_one(selectors.get("title")) if selectors.get("title") else None
        title = self._clean_text((title_node or content).get_text(" ", strip=True) if title_node else chapter["chapterName"])
        text = normalize_chapter_text(content.get_text("\n", strip=True))
        if not text:
            raise ValueError("chapter content is empty")
        return {
            "sourceChapterId": chapter["sourceChapterId"],
            "sourceUrl": url,
            "chapterNo": chapter["chapterNo"],
            "chapterName": title,
            "content": text,
            "contentHash": "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest(),
        }

    def _fetch_chapter(self, task, selectors, chapter, url):
        parts = []
        current_url = url
        seen_urls = set()
        next_chapter_url = None
        max_pages = max(1, min(20, int(selectors.get("maxPages") or 10)))
        for page in range(max_pages):
            if current_url in seen_urls:
                raise ValueError("chapter pagination contains a cycle")
            seen_urls.add(current_url)
            html = self._fetch_with_retry(task, current_url)
            part = self._extract_chapter(html, selectors, chapter, url)
            parts.append(part["content"])
            next_selector = selectors.get("nextPage")
            if not next_selector:
                break
            soup = BeautifulSoup(html, "html.parser")
            next_node = soup.select_one(next_selector)
            if not next_node or not next_node.get("href"):
                next_chapter = soup.select_one(selectors.get("nextChapter")) if selectors.get("nextChapter") else None
                required_text = str(selectors.get("nextChapterText") or "").strip()
                if next_chapter and next_chapter.get("href") and (
                    not required_text or required_text in self._clean_text(next_chapter.get_text(" ", strip=True))
                ):
                    next_chapter_url = urllib.parse.urljoin(current_url, next_chapter["href"])
                    self._validate_public_same_host(next_chapter_url, task["sourceWorkUrl"])
                break
            if page + 1 >= max_pages:
                raise ValueError("chapter pagination exceeds maxPages")
            next_url = urllib.parse.urljoin(current_url, next_node["href"])
            self._validate_public_same_host(next_url, task["sourceWorkUrl"])
            self._polite_delay(task)
            current_url = next_url
        text = normalize_chapter_text("\n\n".join(parts))
        if chapter.get("discoveredFromNext"):
            actual_title = re.sub(r"[（(]\s*\d+\s*/\s*\d+\s*[）)]\s*$", "", part["chapterName"]).strip()
            part["chapterName"] = actual_title or "第{}章".format(chapter["chapterNo"])
            part["chapterNo"] = self._chapter_no(part["chapterName"], chapter["chapterNo"])
            chapter["chapterNo"] = part["chapterNo"]
            chapter["chapterName"] = part["chapterName"]
        else:
            part["chapterName"] = chapter["chapterName"]
        part["content"] = text
        part["contentHash"] = "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()
        if next_chapter_url:
            part["_nextChapterUrl"] = next_chapter_url
        return part

    def _select_range(self, chapters, task):
        cursor = int(task.get("cursorChapterNo") or 0)
        start = max(cursor + 1, int(task.get("startChapterNo") or 1))
        end = int(task["endChapterNo"]) if task.get("endChapterNo") else None
        return [item for item in chapters if item["chapterNo"] >= start and (end is None or item["chapterNo"] <= end)]

    def _expand_url(self, template, task, chapter):
        work_url = task["sourceWorkUrl"]
        work_id = urllib.parse.urlparse(work_url).path.rstrip("/").split("/")[-1]
        values = {
            "url": work_url, "workUrl": work_url, "workId": work_id,
            "title": task.get("sourceWorkTitle") or "",
            "author": task.get("authorName") or "",
        }
        if chapter:
            chapter_id = urllib.parse.urlparse(chapter["sourceChapterId"]).path.rstrip("/").split("/")[-1]
            values.update({"chapterUrl": chapter["sourceChapterId"], "chapterId": chapter["sourceChapterId"], "chapterNo": chapter["chapterNo"], "id": chapter_id})
        result = template
        for key, value in values.items():
            result = result.replace("{" + key + "}", urllib.parse.quote(str(value), safe=":/?#[]@!$&'()*+,;=-._~/%"))
        return urllib.parse.urljoin(work_url, result)

    def _resolve_source_work_url(self, task, selectors):
        """Resolve a fallback search URL to the matching detail URL before reading chapters."""
        search_template = task.get("searchUrlTemplate")
        if not task.get("fallbackId") or not search_template:
            return task["sourceWorkUrl"]
        search = selectors.get("search") or {}
        search_url = self._expand_url(search.get("url") or search_template, task, None)
        self._validate_public_same_host(search_url, task["sourceWorkUrl"])
        if str(search.get("method") or "GET").upper() == "POST":
            params = {str(key): self._expand_search_value(value, task) for key, value in (search.get("params") or {}).items()}
            html = self._fetch_form_with_retry(task, search_url, params)
        else:
            html = self._fetch_with_retry(task, search_url)
        books = self._extract_books(html, selectors.get("bookList") or {}, search_url, None)
        target_title = self._clean_text(task.get("sourceWorkTitle") or "").lower()
        if not target_title:
            raise ValueError("fallback search requires the source work title")
        target_author = self._clean_text(task.get("authorName") or "").lower()
        exact = [book for book in books if self._clean_text(book.get("sourceWorkTitle") or "").lower() == target_title]
        candidates = exact or [book for book in books if target_title in self._clean_text(book.get("sourceWorkTitle") or "").lower()]
        if target_author and len(candidates) > 1:
            authored = [book for book in candidates if target_author in self._clean_text(book.get("authorName") or "").lower()]
            candidates = authored or candidates
        if not candidates:
            raise ValueError("fallback search found no matching work")
        return candidates[0]["sourceWorkUrl"]

    def _expand_search_value(self, value, task):
        result = str(value or "")
        for key, replacement in {"title": task.get("sourceWorkTitle") or "", "author": task.get("authorName") or ""}.items():
            result = result.replace("{" + key + "}", replacement)
        return result

    def _fetch_form_with_retry(self, task, url, params):
        retries = max(0, min(5, int(task.get("maxRetries") or 0)))
        for attempt in range(retries + 1):
            self._wait_for_permit(task)
            try:
                return self._fetch_form(url, params, task)
            except SourceHttpError as exc:
                if exc.status in (401, 403, 429) or attempt >= retries:
                    raise
                time.sleep(min(60, 2 ** attempt))
        raise RuntimeError("request retry exhausted")

    def _fetch_form(self, url, params, task):
        request = urllib.request.Request(url, data=urllib.parse.urlencode(params).encode("utf-8"), headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "User-Agent": "reader-source-worker/1.0 (+authorized-fetch)",
        }, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=(int(task.get("connectTimeoutMs") or 10000) + int(task.get("readTimeoutMs") or 20000)) / 1000) as response:
                if not (200 <= response.status < 300):
                    raise SourceHttpError(response.status, "source returned HTTP {}".format(response.status))
                body = response.read(MAX_RESPONSE_BYTES + 1)
                if len(body) > MAX_RESPONSE_BYTES:
                    raise SourceHttpError(None, "source response exceeds size limit")
                content_type = response.headers.get("Content-Type", "").lower()
                meta_prefix = body[:8192].lower()
                source_declares_gbk = any(marker in meta_prefix for marker in (b"charset=gbk", b"charset=gb18030", b"charset=gb2312"))
                charset = response.headers.get_content_charset() or ("gb18030" if "gbk" in content_type or "gb2312" in content_type or source_declares_gbk else "utf-8")
                return body.decode(charset, errors="replace")
        except urllib.error.HTTPError as exc:
            raise SourceHttpError(exc.code, "source returned HTTP {}".format(exc.code), exc.headers.get("Retry-After"))
        except urllib.error.URLError as exc:
            raise SourceHttpError(None, "source request failed: {}".format(exc.reason))

    def _validate_public_same_host(self, url, expected):
        parsed = urllib.parse.urlparse(url)
        source = urllib.parse.urlparse(expected) if isinstance(expected, str) else None
        if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("source URL must be a credential-free HTTP(S) URL")
        if source and not self._same_authorized_host(parsed.hostname, source.hostname):
            raise ValueError("source URL leaves the approved host")
        if not self.allow_private_for_test:
            for info in socket.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80)):
                address = ipaddress.ip_address(info[4][0])
                if address.is_private or address.is_loopback or address.is_link_local or address.is_multicast or address.is_reserved:
                    raise ValueError("source URL resolves to a non-public address")
        return parsed.hostname

    @staticmethod
    def _same_authorized_host(actual, expected):
        actual = (actual or "").lower().rstrip(".")
        expected = (expected or "").lower().rstrip(".")
        if actual == expected:
            return True
        return actual.removeprefix("www.") == expected.removeprefix("www.")

    def _polite_delay(self, task):
        low = max(0, int(task.get("minDelayMs") or 0)) / 1000
        high = max(low, int(task.get("maxDelayMs") or int(task.get("minDelayMs") or 0))) / 1000
        if high:
            time.sleep(random.uniform(low, high))

    @staticmethod
    def _chapter_no(title, fallback):
        match = re.search(r"第\s*([0-9零〇一二两三四五六七八九十百千万亿]+)\s*(?:章|节|回|集)", title or "")
        if not match:
            return fallback
        token = match.group(1)
        if token.isdigit():
            return int(token)
        return Worker._chinese_number(token, fallback)

    @staticmethod
    def _chinese_number(value, fallback):
        digits = {"零": 0, "〇": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
                  "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
        units = {"十": 10, "百": 100, "千": 1000, "万": 10000, "亿": 100000000}
        total = 0
        section = 0
        number = 0
        try:
            for char in value:
                if char in digits:
                    number = digits[char]
                elif char in units:
                    unit = units[char]
                    if unit < 10000:
                        section += (number or 1) * unit
                    else:
                        total += (section + number) * unit
                        section, number = 0, 0
                else:
                    return fallback
            return total + section + number
        except (TypeError, ValueError):
            return fallback

    @classmethod
    def _chapter_no_from_url(cls, url, title, fallback):
        title_no = cls._chapter_no(title, 0)
        if title_no > 0:
            return title_no
        path = urllib.parse.urlparse(url).path.rstrip("/")
        last = path.rsplit("/", 1)[-1]
        if last.lower().endswith(".html"):
            last = last[:-5]
        if last.isdigit() and int(last) > 0:
            return int(last)
        return fallback

    @staticmethod
    def _clean_text(value):
        return re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+", " ", value or "")).strip()


if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    Worker().run_forever()
