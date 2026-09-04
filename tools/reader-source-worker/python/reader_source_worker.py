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
from threading import Event, Thread

from bs4 import BeautifulSoup


LOG = logging.getLogger("reader-source-worker")
CHAPTER_BATCH_SIZE = 20
MAX_RESPONSE_BYTES = 8 * 1024 * 1024


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
        self.stop_event = Event()
        if not self.secret:
            raise RuntimeError("READER_SOURCE_WORKER_SECRET is required")

    def run_forever(self):
        while not self.stop_event.is_set():
            task = self._post("/reader/worker/source/runs/claim", {
                "executorType": "PYTHON",
                "workerId": self.worker_id,
            })
            if not task:
                self.stop_event.wait(self.poll_seconds)
                continue
            LOG.info("claimed run=%s task=%s", task.get("runId"), task.get("taskId"))
            self._run_task(task)

    def _run_task(self, task):
        heartbeat_stop = Event()
        heartbeat = Thread(target=self._heartbeat_loop, args=(task, heartbeat_stop), daemon=True)
        heartbeat.start()
        try:
            selectors = self._load_selectors(task.get("selectorJson"))
            catalog_url = self._expand_url(task.get("catalogUrlTemplate") or task["sourceWorkUrl"], task, None)
            self._validate_public_same_host(catalog_url, task["sourceWorkUrl"])
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
                        chapter_html = self._fetch_with_retry(task, chapter_url)
                        metrics["requests"] += 1
                        item = self._extract_chapter(chapter_html, selectors["chapter"], chapter, chapter_url)
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

    def _heartbeat_loop(self, task, stop_event):
        interval = max(10, int(task.get("claimLeaseSeconds", 90)) // 3)
        while not stop_event.wait(interval):
            try:
                self._post("/reader/worker/source/runs/{}/heartbeat".format(task["runId"]), {
                    "runId": task["runId"],
                    "runToken": task["runToken"],
                    "workerId": self.worker_id,
                })
            except Exception as exc:
                LOG.warning("heartbeat failed run=%s: %s", task.get("runId"), exc)
                return

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
                charset = response.headers.get_content_charset() or "utf-8"
                return body.decode(charset, errors="replace")
        except urllib.error.HTTPError as exc:
            retry_after = exc.headers.get("Retry-After")
            raise SourceHttpError(exc.code, "source returned HTTP {}".format(exc.code), retry_after)
        except urllib.error.URLError as exc:
            raise SourceHttpError(None, "source request failed: {}".format(exc.reason))

    def _wait_for_permit(self, task):
        path = "/reader/worker/source/runs/{}/permit".format(task["runId"])
        while True:
            permit = self._post(path, {
                "runId": task["runId"],
                "runToken": task["runToken"],
                "workerId": self.worker_id,
            })
            if permit.get("allowed"):
                return
            delay = max(0.1, min(300, float(permit.get("retryAfterMs") or 1000) / 1000))
            LOG.info("permit denied run=%s retryIn=%.1fs reason=%s", task.get("runId"), delay, permit.get("reason"))
            time.sleep(delay)

    def _report_error(self, task, exc, url):
        status = getattr(exc, "status", None)
        retry_at = None
        retry_after = getattr(exc, "retry_after", None)
        if retry_after and str(retry_after).isdigit():
            retry_at = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=int(retry_after))).isoformat()
        try:
            self._post("/reader/worker/source/runs/{}/error".format(task["runId"]), {
                "runId": task["runId"],
                "runToken": task["runToken"],
                "workerId": self.worker_id,
                "errorType": "HTTP" if status else "PARSE",
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
        required = (catalog.get("item"), catalog.get("title"), chapter.get("content"))
        if not all(isinstance(value, str) and value.strip() for value in required):
            raise ValueError("catalog.item, catalog.title and chapter.content CSS selectors are required")
        return {"catalog": catalog, "chapter": chapter}

    def _extract_catalog(self, html, selectors, base_url):
        soup = BeautifulSoup(html, "html.parser")
        result = []
        for index, node in enumerate(soup.select(selectors["item"]), 1):
            anchor = node.select_one("a[href]") or (node if node.name == "a" and node.get("href") else None)
            if not anchor:
                continue
            url = urllib.parse.urljoin(base_url, anchor["href"])
            title_node = node.select_one(selectors["title"]) or anchor
            title = self._clean_text(title_node.get_text(" ", strip=True))
            chapter_no = self._chapter_no(title, index)
            result.append({"sourceChapterId": url, "url": url, "chapterNo": chapter_no, "chapterName": title})
        return result

    def _extract_chapter(self, html, selectors, chapter, url):
        soup = BeautifulSoup(html, "html.parser")
        content = soup.select_one(selectors["content"])
        if not content:
            raise ValueError("chapter content selector matched no element")
        title_node = soup.select_one(selectors.get("title")) if selectors.get("title") else None
        title = self._clean_text((title_node or content).get_text(" ", strip=True) if title_node else chapter["chapterName"])
        text = self._clean_text(content.get_text("\n", strip=True))
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

    def _select_range(self, chapters, task):
        cursor = int(task.get("cursorChapterNo") or 0)
        start = max(cursor + 1, int(task.get("startChapterNo") or 1))
        end = int(task["endChapterNo"]) if task.get("endChapterNo") else None
        return [item for item in chapters if item["chapterNo"] >= start and (end is None or item["chapterNo"] <= end)]

    def _expand_url(self, template, task, chapter):
        work_url = task["sourceWorkUrl"]
        work_id = urllib.parse.urlparse(work_url).path.rstrip("/").split("/")[-1]
        values = {"url": work_url, "workUrl": work_url, "id": work_id}
        if chapter:
            chapter_id = urllib.parse.urlparse(chapter["sourceChapterId"]).path.rstrip("/").split("/")[-1]
            values.update({"chapterId": chapter["sourceChapterId"], "chapterNo": chapter["chapterNo"], "id": chapter_id})
        result = template
        for key, value in values.items():
            result = result.replace("{" + key + "}", urllib.parse.quote(str(value), safe=":/?#[]@!$&'()*+,;=-._~/%"))
        return urllib.parse.urljoin(work_url, result)

    def _validate_public_same_host(self, url, expected):
        parsed = urllib.parse.urlparse(url)
        source = urllib.parse.urlparse(expected) if isinstance(expected, str) else None
        if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("source URL must be a credential-free HTTP(S) URL")
        if source and parsed.hostname.lower() != (source.hostname or "").lower():
            raise ValueError("source URL leaves the approved host")
        for info in socket.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80)):
            address = ipaddress.ip_address(info[4][0])
            if address.is_private or address.is_loopback or address.is_link_local or address.is_multicast or address.is_reserved:
                raise ValueError("source URL resolves to a non-public address")
        return parsed.hostname

    def _polite_delay(self, task):
        low = max(0, int(task.get("minDelayMs") or 0)) / 1000
        high = max(low, int(task.get("maxDelayMs") or int(task.get("minDelayMs") or 0))) / 1000
        if high:
            time.sleep(random.uniform(low, high))

    @staticmethod
    def _chapter_no(title, fallback):
        match = re.search(r"(?:第\s*)?(\d{1,7})", title or "")
        return int(match.group(1)) if match else fallback

    @staticmethod
    def _clean_text(value):
        return re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+", " ", value or "")).strip()


if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    Worker().run_forever()
