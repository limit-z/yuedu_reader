#!/usr/bin/env python3
"""Collect traceable portrait and landscape book-cover candidates.

This standalone tool is intentionally limited to public Open Library and Google
Books endpoints. It does not bypass login, CAPTCHA, paywalls, or rate limits.
The Java service uses the same provider contract and uploads/binds the results
after a chapter collection task has completed.
"""

import argparse
import hashlib
import ipaddress
import json
import pathlib
import socket
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone

try:
    from PIL import Image
except ImportError:
    Image = None


SEARCH_HOSTS = {"openlibrary.org", "www.googleapis.com"}
IMAGE_HOSTS = {
    "covers.openlibrary.org",
    "books.google.com",
    "books.googleusercontent.com",
    "googleusercontent.com",
}
MAX_BYTES = 5 * 1024 * 1024


def checked_url(raw, allowed_hosts):
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("source URL must be HTTP(S) with a hostname")
    host = parsed.hostname.lower()
    if not any(host == item or host.endswith("." + item) for item in allowed_hosts):
        raise ValueError("source host is not allowlisted")
    for result in socket.getaddrinfo(host, None):
        address = ipaddress.ip_address(result[4][0])
        if address.is_private or address.is_loopback or address.is_link_local or address.is_multicast:
            raise ValueError("source host resolves to a private address")
    return parsed.geturl()


def get_json(url):
    request = urllib.request.Request(checked_url(url, SEARCH_HOSTS), headers={
        "Accept": "application/json",
        "User-Agent": "ReaderCoverCollector/1.0",
    })
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read(MAX_BYTES).decode("utf-8"))


def search(title, author):
    query = (title + " " + (author or "未知作者")).strip()
    results = []
    open_url = "https://openlibrary.org/search.json?" + urllib.parse.urlencode({
        "q": query, "limit": 12, "fields": "key,title,cover_i,author_name",
    })
    try:
        for item in get_json(open_url).get("docs", []):
            cover_id = item.get("cover_i")
            key = item.get("key")
            if cover_id and key:
                results.append({
                    "provider": "Open Library",
                    "page_url": "https://openlibrary.org" + key,
                    "image_url": f"https://covers.openlibrary.org/b/id/{cover_id}-L.jpg",
                })
    except Exception as error:
        print(f"Open Library search failed: {error}", file=sys.stderr)

    google_url = "https://www.googleapis.com/books/v1/volumes?" + urllib.parse.urlencode({
        "q": "intitle:" + query, "maxResults": 12,
    })
    try:
        for item in get_json(google_url).get("items", []):
            info = item.get("volumeInfo", {})
            image_url = info.get("imageLinks", {}).get("thumbnail")
            page_url = info.get("infoLink")
            if image_url and page_url:
                results.append({
                    "provider": "Google Books",
                    "page_url": page_url,
                    "image_url": image_url.replace("http://", "https://"),
                })
    except Exception as error:
        print(f"Google Books search failed: {error}", file=sys.stderr)
    return query, results


def download(candidate):
    request = urllib.request.Request(checked_url(candidate["image_url"], IMAGE_HOSTS), headers={
        "Accept": "image/avif,image/webp,image/jpeg,image/png",
        "User-Agent": "ReaderCoverCollector/1.0",
    })
    with urllib.request.urlopen(request, timeout=20) as response:
        data = response.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise ValueError("image exceeds 5MB")
    if not Image:
        raise RuntimeError("Pillow is required: pip install Pillow")
    image = Image.open(__import__("io").BytesIO(data))
    image.load()
    if image.width < 100 or image.height < 100:
        raise ValueError("image is smaller than 100x100")
    return image.convert("RGB")


def landscape(image):
    canvas = Image.new("RGB", (1200, 675), "#F8F5EF")
    scale = min(1128 / image.width, 603 / image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    resized = image.resize(size, Image.Resampling.LANCZOS)
    canvas.paste(resized, ((1200 - size[0]) // 2, (675 - size[1]) // 2))
    return canvas


def collect(title, author, output, target=3):
    output = pathlib.Path(output)
    output.mkdir(parents=True, exist_ok=True)
    query, candidates = search(title, author)
    seen = set()
    records = []
    for item in candidates:
        if len([record for record in records if record["orientation"] == "PORTRAIT"]) >= target \
                and len([record for record in records if record["orientation"] == "LANDSCAPE"]) >= target:
            break
        try:
            source = download(item)
            source_bytes = __import__("io").BytesIO()
            source.save(source_bytes, format="PNG")
            portrait_hash = hashlib.sha256(source_bytes.getvalue()).hexdigest()
            if portrait_hash not in seen:
                portrait_file = output / f"portrait-{len(records) + 1}.png"
                source.save(portrait_file, format="PNG")
                seen.add(portrait_hash)
                records.append({**item, "orientation": "PORTRAIT", "file": str(portrait_file),
                                "content_hash": portrait_hash, "width": source.width, "height": source.height})
            derived = landscape(source)
            derived_bytes = __import__("io").BytesIO()
            derived.save(derived_bytes, format="PNG")
            landscape_hash = hashlib.sha256(derived_bytes.getvalue()).hexdigest()
            if landscape_hash not in seen:
                landscape_file = output / f"landscape-{len(records) + 1}.png"
                derived.save(landscape_file, format="PNG")
                seen.add(landscape_hash)
                records.append({**item, "provider": item["provider"] + " (derived landscape)",
                                "orientation": "LANDSCAPE", "file": str(landscape_file),
                                "content_hash": landscape_hash, "width": derived.width, "height": derived.height})
        except Exception as error:
            print(f"candidate failed: {error}", file=sys.stderr)
    manifest = {
        "title": title,
        "author": author or "未知作者",
        "query": query,
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "target_per_orientation": target,
        "candidates": records,
        "source_policy": "Public Open Library and Google Books endpoints; no access-control bypass.",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main():
    parser = argparse.ArgumentParser(description="Collect traceable novel cover candidates")
    parser.add_argument("--title", required=True)
    parser.add_argument("--author", default="未知作者")
    parser.add_argument("--output", default="./cover-candidates")
    parser.add_argument("--target", type=int, default=3)
    args = parser.parse_args()
    if args.target < 3:
        parser.error("--target must be at least 3")
    manifest = collect(args.title, args.author, args.output, args.target)
    print(json.dumps({"portrait": sum(x["orientation"] == "PORTRAIT" for x in manifest["candidates"]),
                      "landscape": sum(x["orientation"] == "LANDSCAPE" for x in manifest["candidates"]),
                      "manifest": str(pathlib.Path(args.output) / "manifest.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
