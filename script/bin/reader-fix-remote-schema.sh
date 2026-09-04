#!/usr/bin/env bash
set -euo pipefail

export READER_DB_HOST="${READER_DB_HOST:?请设置 READER_DB_HOST}"
export READER_DB_PORT="${READER_DB_PORT:-3306}"
export READER_DB_USER="${READER_DB_USER:?请设置 READER_DB_USER}"
export READER_DB_PASSWORD="${READER_DB_PASSWORD:?请设置 READER_DB_PASSWORD}"
export READER_DB_NAME="${READER_DB_NAME:-reader}"

python3 - <<'PY'
import os
import pymysql

conn = pymysql.connect(
    host=os.environ['READER_DB_HOST'],
    port=int(os.environ['READER_DB_PORT']),
    user=os.environ['READER_DB_USER'],
    password=os.environ['READER_DB_PASSWORD'],
    database=os.environ['READER_DB_NAME'],
    charset='utf8mb4',
    autocommit=True,
)
cur = conn.cursor()

cur.execute(f"ALTER DATABASE `{os.environ['READER_DB_NAME']}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
cur.execute(
    "SELECT table_name FROM information_schema.tables "
    "WHERE table_schema=%s AND table_name LIKE 'reader\\_%' ORDER BY table_name",
    ('reader',),
)
for (table_name,) in cur.fetchall():
    cur.execute(f"ALTER TABLE `{table_name}` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    print(f"[CHARSET] {table_name} => utf8mb4_unicode_ci")

targets = {
    'reader_work': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_novel_chapter': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_comic_chapter': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_comic_page': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_import_task': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
        ('total_units', 'int NOT NULL DEFAULT 0'),
        ('processed_units', 'int NOT NULL DEFAULT 0'),
        ('progress_percent', 'int NOT NULL DEFAULT 0'),
        ('progress_message', 'varchar(255) DEFAULT NULL'),
    ],
    'reader_import_file': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_content_audit': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_bookshelf': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_reading_history': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
    ],
    'reader_reading_progress': [
        ('create_dept', 'bigint(20) DEFAULT NULL'),
        ('create_by', 'bigint(20) DEFAULT NULL'),
        ('create_time', 'datetime DEFAULT NULL'),
        ('update_by', 'bigint(20) DEFAULT NULL'),
        ('update_time', 'datetime DEFAULT NULL'),
    ],
}

for table, columns in targets.items():
    cur.execute(
        "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=%s AND table_name=%s",
        ('reader', table),
    )
    existing = {row[0] for row in cur.fetchall()}
    for column, definition in columns:
        if column in existing:
            continue
        cur.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
        print(f"[ADD] {table}.{column}")

conn.close()
PY
