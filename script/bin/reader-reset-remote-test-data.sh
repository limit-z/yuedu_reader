#!/usr/bin/env bash
set -euo pipefail

# 阅读器远端测试数据重置脚本
# 用途：
# 1. 删除阅读器测试导入产生的 MinIO 物理文件
# 2. 删除对应的 sys_oss 记录
# 3. 清空阅读器 reader_* 业务表，便于重新导入验证
#
# 使用方式：
#   READER_DB_HOST=106.55.169.147 \
#   READER_DB_PORT=7955 \
#   READER_DB_NAME=reader \
#   READER_DB_USER=root \
#   READER_DB_PASSWORD='******' \
#   /Users/yabin/code/ruoyi/RuoYi-Vue-Plus/script/bin/reader-reset-remote-test-data.sh --reader-only
#
# 模式说明：
#   --reader-only  只删除阅读器业务关联的 OSS/MinIO 文件与记录，然后清空 reader_* 业务表
#   --all-bucket   清空当前启用的整个 MinIO bucket，并删除全部 minio 类型 sys_oss 记录，再清空 reader_* 业务表

MODE="${1:---reader-only}"

if [[ "${MODE}" != "--reader-only" && "${MODE}" != "--all-bucket" ]]; then
  echo "用法错误：仅支持 --reader-only 或 --all-bucket"
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "缺少 python3，无法执行重置脚本"
  exit 1
fi

MINIO_MC_BIN="${MINIO_MC_BIN:-mc}"

if ! command -v "${MINIO_MC_BIN}" >/dev/null 2>&1; then
  echo "缺少 MinIO Client，可通过 MINIO_MC_BIN 指定可执行文件路径"
  exit 1
fi

if "${MINIO_MC_BIN}" --version 2>/dev/null | grep -q "GNU Midnight Commander"; then
  echo "当前 ${MINIO_MC_BIN} 指向的是 GNU Midnight Commander，不是 MinIO Client"
  echo "请安装 MinIO Client，并通过 MINIO_MC_BIN=/your/path/minio-mc 指定正确可执行文件"
  exit 1
fi

: "${READER_DB_HOST:?请设置 READER_DB_HOST}"
: "${READER_DB_PORT:?请设置 READER_DB_PORT}"
: "${READER_DB_NAME:?请设置 READER_DB_NAME}"
: "${READER_DB_USER:?请设置 READER_DB_USER}"
: "${READER_DB_PASSWORD:?请设置 READER_DB_PASSWORD}"

python3 - "${MODE}" <<'PY'
import json
import os
import subprocess
import sys

import pymysql


MODE = sys.argv[1]


def connect():
    return pymysql.connect(
        host=os.environ["READER_DB_HOST"],
        port=int(os.environ["READER_DB_PORT"]),
        user=os.environ["READER_DB_USER"],
        password=os.environ["READER_DB_PASSWORD"],
        database=os.environ["READER_DB_NAME"],
        charset="utf8mb4",
        autocommit=True,
    )


def fetch_active_minio_config(cur):
    cur.execute(
        """
        SELECT config_key, access_key, secret_key, bucket_name, endpoint, is_https
        FROM sys_oss_config
        WHERE config_key = 'minio' AND status = 'Y'
        LIMIT 1
        """
    )
    row = cur.fetchone()
    if not row:
        raise RuntimeError("未找到启用中的 minio 配置")
    return {
        "config_key": row[0],
        "access_key": row[1],
        "secret_key": row[2],
        "bucket_name": row[3],
        "endpoint": row[4],
        "is_https": row[5],
    }


def fetch_reader_related_oss(cur):
    cur.execute(
        """
        SELECT DISTINCT s.oss_id, s.file_name, s.original_name, s.url, s.service
        FROM sys_oss s
        WHERE s.oss_id IN (
            SELECT oss_id FROM reader_import_task WHERE oss_id IS NOT NULL
            UNION
            SELECT oss_id FROM reader_import_file WHERE oss_id IS NOT NULL
        )
        OR s.url IN (
            SELECT cover_url FROM reader_work WHERE cover_url IS NOT NULL AND cover_url <> ''
        )
        ORDER BY s.oss_id
        """
    )
    return [
        {
            "oss_id": row[0],
            "file_name": row[1],
            "original_name": row[2],
            "url": row[3],
            "service": row[4],
        }
        for row in cur.fetchall()
    ]


MC_BIN = os.environ.get("MINIO_MC_BIN", "mc")


def run(cmd):
    print("[CMD]", " ".join(cmd))
    subprocess.run(cmd, check=True)


def set_mc_alias(alias_name, config):
    scheme = "https" if config["is_https"] == "Y" else "http"
    endpoint = config["endpoint"]
    if endpoint.startswith("http://") or endpoint.startswith("https://"):
        server = endpoint
    else:
        server = f"{scheme}://{endpoint}"
    run([MC_BIN, "alias", "set", alias_name, server, config["access_key"], config["secret_key"]])


def remove_reader_objects(alias_name, bucket_name, oss_rows):
    removed = 0
    for row in oss_rows:
        if row["service"] != "minio":
            continue
        if not row["file_name"]:
            continue
        run([MC_BIN, "rm", "--force", f"{alias_name}/{bucket_name}/{row['file_name']}"])
        removed += 1
    return removed


def purge_bucket(alias_name, bucket_name):
    run([MC_BIN, "rm", "--recursive", "--force", f"{alias_name}/{bucket_name}"])


def reset_reader_business_tables(cur):
    cur.execute("SET FOREIGN_KEY_CHECKS = 0")
    for table in [
        "reader_bookshelf",
        "reader_reading_history",
        "reader_reading_progress",
        "reader_content_audit",
        "reader_import_file",
        "reader_import_task",
        "reader_comic_page",
        "reader_comic_chapter",
        "reader_novel_chapter_content",
        "reader_novel_chapter",
        "reader_work",
    ]:
        cur.execute(f"TRUNCATE TABLE {table}")
        print("[TRUNCATE]", table)
    cur.execute("SET FOREIGN_KEY_CHECKS = 1")


def delete_reader_sys_oss(cur, oss_rows):
    oss_ids = [row["oss_id"] for row in oss_rows if row["oss_id"] is not None]
    if not oss_ids:
        print("[INFO] 未找到需要删除的 sys_oss 记录")
        return
    placeholders = ",".join(["%s"] * len(oss_ids))
    cur.execute(f"DELETE FROM sys_oss WHERE oss_id IN ({placeholders})", oss_ids)
    print("[DELETE] sys_oss rows =", cur.rowcount)


def delete_all_minio_sys_oss(cur):
    cur.execute("DELETE FROM sys_oss WHERE service = 'minio'")
    print("[DELETE] all minio sys_oss rows =", cur.rowcount)


def print_counts(cur, stage):
    print(f"[COUNTS] {stage}")
    for table in [
        "reader_work",
        "reader_novel_chapter",
        "reader_novel_chapter_content",
        "reader_comic_chapter",
        "reader_comic_page",
        "reader_import_task",
        "reader_import_file",
        "reader_content_audit",
        "reader_bookshelf",
        "reader_reading_history",
        "reader_reading_progress",
    ]:
        cur.execute(f"SELECT COUNT(*) FROM {table}")
        print(f"  - {table}: {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM sys_oss")
    print(f"  - sys_oss: {cur.fetchone()[0]}")


def main():
    alias_name = "reader-reset-tmp"
    conn = connect()
    try:
        with conn.cursor() as cur:
            config = fetch_active_minio_config(cur)
            set_mc_alias(alias_name, config)
            print_counts(cur, "BEFORE")
            if MODE == "--reader-only":
                oss_rows = fetch_reader_related_oss(cur)
                print("[INFO] reader-related sys_oss rows =", len(oss_rows))
                removed = remove_reader_objects(alias_name, config["bucket_name"], oss_rows)
                print("[INFO] removed minio objects =", removed)
                delete_reader_sys_oss(cur, oss_rows)
                reset_reader_business_tables(cur)
            else:
                purge_bucket(alias_name, config["bucket_name"])
                delete_all_minio_sys_oss(cur)
                reset_reader_business_tables(cur)
            print_counts(cur, "AFTER")
    finally:
        try:
            subprocess.run([MC_BIN, "alias", "rm", alias_name], check=False)
        finally:
            conn.close()


main()
PY
