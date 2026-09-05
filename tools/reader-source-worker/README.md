# Reader source workers

This directory contains reference workers for the reader source center. They are
HTTP clients for the worker protocol implemented by `ruoyi-reader`.

Only run a worker for a source that the platform is authorized to access. The
workers do not provide proxy rotation, CAPTCHA bypass, fingerprint spoofing,
paywall bypass, or ban evasion. Every source request first obtains a permit from
the Java service, so all workers share the Redis rate-limit state.

## Java

```bash
cd java
export READER_SOURCE_API_BASE_URL=http://127.0.0.1:8080
export READER_SOURCE_WORKER_SECRET='set-out-of-band'
export READER_SOURCE_WORKER_ID=java-worker-01
mvn -q compile exec:java
```

The Java worker uses Jsoup for the same declaration-only CSS selector contract
as the Python and Go workers. It also renews the run lease and reports parsing
and HTTP failures to the server.

## Python

```bash
cd python
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt

export READER_SOURCE_API_BASE_URL=http://127.0.0.1:8080
export READER_SOURCE_WORKER_SECRET='set-out-of-band'
export READER_SOURCE_WORKER_ID=python-worker-01
python reader_source_worker.py
```

The Python worker supports CSS selectors from the declaration-only rule JSON:

```json
{
  "catalog": {"item": ".chapter-item", "title": ".chapter-title"},
  "chapter": {"title": "h1", "content": ".content"}
}
```

It sends at most 20 chapters per result batch and reports a SHA-256 hash for
each chapter body. The server remains the source of truth for task status,
leases, idempotency, review state, and rate limits.

## Go

```bash
cd go
go run .
```

The Go worker uses the same environment variables and protocol. Its CSS support
uses `goquery`; keep the module dependencies pinned and review them before a
production deployment.

## Environment variables

`READER_SOURCE_API_BASE_URL` defaults to `http://127.0.0.1:8080`.
`READER_SOURCE_WORKER_SECRET` is required and must match the server environment.
`READER_SOURCE_WORKER_ID` defaults to the process hostname plus `-worker`.
`READER_SOURCE_POLL_SECONDS` defaults to 5.

`READER_SOURCE_ALLOW_PRIVATE_FOR_TEST=true` is an explicit local-test switch
for a loopback Mock source only. Set the same variable for the Java backend
when running the local Mock end-to-end test. It must remain unset or false in
production; the default rejects private, loopback, link-local, multicast,
reserved, and metadata addresses.
