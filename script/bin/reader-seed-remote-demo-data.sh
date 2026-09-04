#!/usr/bin/env bash
set -euo pipefail

# 阅读器远端演示数据初始化脚本
# 用途：
# 1. 为“我的 / 意见反馈 / 消息中心 / 书城公告与专题”补充首批可评审演示数据
# 2. 保证脚本可重复执行，重复执行时不会无限新增同类演示数据
# 3. 与 H5 v1.0 已定稿功能保持同一套基础数据语义
#
# 使用方式：
#   READER_DB_HOST=106.55.169.147 \
#   READER_DB_PORT=7955 \
#   READER_DB_NAME=reader \
#   READER_DB_USER=root \
#   READER_DB_PASSWORD='******' \
#   bash /Users/yabin/code/ruoyi/RuoYi-Vue-Plus/script/bin/reader-seed-remote-demo-data.sh

: "${READER_DB_HOST:?请设置 READER_DB_HOST}"
: "${READER_DB_PORT:?请设置 READER_DB_PORT}"
: "${READER_DB_NAME:?请设置 READER_DB_NAME}"
: "${READER_DB_USER:?请设置 READER_DB_USER}"
: "${READER_DB_PASSWORD:?请设置 READER_DB_PASSWORD}"

python3 - <<'PY'
import json
import os
from datetime import datetime, timedelta

import pymysql


def connect():
    return pymysql.connect(
        host=os.environ["READER_DB_HOST"],
        port=int(os.environ["READER_DB_PORT"]),
        user=os.environ["READER_DB_USER"],
        password=os.environ["READER_DB_PASSWORD"],
        database=os.environ["READER_DB_NAME"],
        charset="utf8mb4",
        autocommit=False,
    )


def fetch_one(cur, sql, args=None):
    cur.execute(sql, args or ())
    return cur.fetchone()


def ensure_visitor(cur):
    visitor_id = "demo-visitor-v1"
    row = fetch_one(cur, "SELECT id FROM reader_visitor_account WHERE visitor_id = %s LIMIT 1", (visitor_id,))
    if row:
        return row[0]
    preferences = {
        "appTheme": "sunrise-cinema",
        "readerTheme": "paper-sun",
        "fontSize": 20,
        "lineHeight": 1.9,
        "pageMode": "scroll",
        "eyeCareMode": False,
    }
    cur.execute(
        """
        INSERT INTO reader_visitor_account
        (visitor_id, nick_name, avatar_style, gender, mobile, wechat_no, preferences_json, last_client_type, create_time, update_time)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        """,
        (
            visitor_id,
            "演示读者小羽",
            "aurora-girl",
            "FEMALE",
            "13800138000",
            "reader-demo-yu",
            json.dumps(preferences, ensure_ascii=False),
            "H5",
        ),
    )
    return cur.lastrowid


def ensure_feedback(cur, visitor_id):
    cur.execute("DELETE FROM reader_user_feedback WHERE reader_id = %s AND account_type = 'VISITOR'", (visitor_id,))
    base_time = datetime.now() - timedelta(days=2)
    rows = [
        ("体验建议", "阅读设置里的字号和背景如果能跨设备同步，会更方便我在手机和平板切换。", "PENDING", None, None, None),
        ("功能异常", "连续阅读时切到下一章后，希望顶部章节名和底部进度都能立即刷新。", "PROCESSING", None, None, None),
        ("内容问题", "某些作品目录里章节标题过长时容易换行，建议后台补一个简版标题字段。", "REPLIED", "我们已经在排查目录排版和短标题方案，后续会同步优化。", 1, base_time + timedelta(days=1, hours=6)),
        ("账号问题", "后续如果支持微信和手机号绑定，希望可以保留游客书架和阅读进度。", "DONE", "已纳入登录合并设计，正式接入登录时会自动迁移游客期书架与进度。", 1, base_time + timedelta(days=1, hours=10)),
    ]
    for index, row in enumerate(rows):
        create_time = base_time + timedelta(hours=index * 4)
        cur.execute(
            """
            INSERT INTO reader_user_feedback
            (reader_id, account_type, feedback_type, feedback_content, contact_mobile, contact_wechat, nick_name, status,
             reply_content, reply_by, reply_time, create_time, update_time)
            VALUES (%s, 'VISITOR', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
            """,
            (
                visitor_id,
                row[0],
                row[1],
                "13800138000",
                "reader-demo-yu",
                "演示读者小羽",
                row[2],
                row[3],
                row[4],
                row[5],
                create_time,
            ),
        )


def ensure_notices(cur):
    cur.execute("DELETE FROM reader_home_notice WHERE create_by IS NULL AND notice_title LIKE '演示公告：%'")
    rows = [
        ("演示公告：H5 v1.0 已定稿，小程序页面将按当前版式逐步重建。", "当前书城、我的、阅读器的交互基线已经统一，后续将优先补真实接口。", "NONE", None, 10),
        ("演示公告：意见反馈已接入真实工单表，提交后后台可直接处理。", "你现在在小程序“意见反馈”里提交的内容，后续都会进入后台反馈工单列表。", "FEEDBACK", None, 20),
        ("演示公告：专题、公告、消息、点评的数据库基础表已建立。", "下一步会继续把首页运营位和消息中心从静态数据切到正式接口。", "TOPIC", "cinema-original", 30),
    ]
    for title, content, target_type, target_value, sort_no in rows:
        cur.execute(
            """
            INSERT INTO reader_home_notice
            (notice_title, notice_content, target_type, target_value, sort_no, status, start_time, create_time, update_time)
            VALUES (%s, %s, %s, %s, %s, '1', NOW(), NOW(), NOW())
            """,
            (title, content, target_type, target_value, sort_no),
        )


def ensure_banners(cur, work_id):
    cur.execute("DELETE FROM reader_home_banner WHERE create_by IS NULL AND banner_title LIKE '演示横幅：%'")
    rows = [
        ("演示横幅：继续阅读与书城推荐已开始统一到同一聚合接口", "后续首页会优先展示公告、横幅、榜单和专题，再承接作品详情跳转。", "#F3E1C6", "WORK", str(work_id), 10),
        ("演示横幅：热映影视原著专题", "适合放书城上半区的大图推荐位，点击后进入专题作品集合。", "#E8C5A0", "TOPIC", "cinema-original", 20),
    ]
    for title, subtitle, bg, target_type, target_value, sort_no in rows:
        cur.execute(
            """
            INSERT INTO reader_home_banner
            (banner_title, banner_subtitle, background_color, target_type, target_value, sort_no, status, start_time, create_time, update_time)
            VALUES (%s, %s, %s, %s, %s, %s, '1', NOW(), NOW(), NOW())
            """,
            (title, subtitle, bg, target_type, target_value, sort_no),
        )


def ensure_topics(cur, work_id):
    cur.execute("DELETE FROM reader_topic_work WHERE topic_id IN (SELECT id FROM reader_topic WHERE create_by IS NULL AND topic_key IN ('cinema-original', 'classic-fantasy', 'slow-healing'))")
    cur.execute("DELETE FROM reader_topic WHERE create_by IS NULL AND topic_key IN ('cinema-original', 'classic-fantasy', 'slow-healing')")
    topics = [
        ("cinema-original", "热映影视原著", "聚合适合首页大卡展示的影视改编原著作品。", "热映", "NOVEL", "/pages/topic/index?topicKey=cinema-original", 10),
        ("classic-fantasy", "玄幻十年经典", "承接长线热读、完结精品与经典口碑书。", "经典", "NOVEL", "/pages/topic/index?topicKey=classic-fantasy", 20),
        ("slow-healing", "治愈慢读推荐", "适合夜读、睡前和情绪放松场景的慢节奏阅读专题。", "慢读", "NOVEL", "/pages/topic/index?topicKey=slow-healing", 30),
    ]
    topic_ids = []
    for topic_key, topic_name, topic_desc, badge_text, work_type, more_path, sort_no in topics:
        cur.execute(
            """
            INSERT INTO reader_topic
            (topic_key, topic_name, topic_desc, badge_text, work_type, more_path, show_home, sort_no, status, start_time, create_time, update_time)
            VALUES (%s, %s, %s, %s, %s, %s, '1', %s, '1', NOW(), NOW(), NOW())
            """,
            (topic_key, topic_name, topic_desc, badge_text, work_type, more_path, sort_no),
        )
        topic_ids.append(cur.lastrowid)
    for index, topic_id in enumerate(topic_ids):
        cur.execute(
            """
            INSERT INTO reader_topic_work
            (topic_id, work_id, sort_no, remark, create_time, update_time)
            VALUES (%s, %s, %s, %s, NOW(), NOW())
            """,
            (topic_id, work_id, 10, f"演示专题作品 #{index + 1}"),
        )


def ensure_messages(cur, visitor_id):
    cur.execute("DELETE FROM reader_user_message WHERE reader_id = %s AND account_type = 'VISITOR'", (visitor_id,))
    rows = [
        ("NOTICE", "系统公告已更新", "书城滚动公告后续会接入真实公告表，这里先预置一条演示消息。", "NONE", None, "0"),
        ("UPDATE", "作品更新提醒", "你收藏的作品《谁还不是个修行者了》有新章节导入，可继续阅读。", "WORK", None, "0"),
        ("FEEDBACK", "反馈已收到", "你提交的体验建议已经进入后台工单队列，管理员处理中。", "FEEDBACK", "1", "1"),
    ]
    work_id_row = fetch_one(cur, "SELECT id FROM reader_work ORDER BY id DESC LIMIT 1")
    work_id = str(work_id_row[0]) if work_id_row else None
    for message_type, title, content, link_type, link_value, read_status in rows:
        actual_value = work_id if link_type == "WORK" else link_value
        cur.execute(
            """
            INSERT INTO reader_user_message
            (reader_id, account_type, message_type, title, content, link_type, link_value, read_status, read_time, create_time, update_time)
            VALUES (%s, 'VISITOR', %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
            """,
            (
                visitor_id,
                message_type,
                title,
                content,
                link_type,
                actual_value,
                read_status,
                datetime.now() if read_status == "1" else None,
            ),
        )


def main():
    conn = connect()
    try:
        with conn.cursor() as cur:
            work_row = fetch_one(cur, "SELECT id FROM reader_work ORDER BY id DESC LIMIT 1")
            if not work_row:
                raise RuntimeError("当前 reader_work 为空，无法建立横幅和专题演示数据，请先至少保留一部作品。")
            work_id = work_row[0]
            visitor_id = ensure_visitor(cur)
            ensure_feedback(cur, visitor_id)
            ensure_notices(cur)
            ensure_banners(cur, work_id)
            ensure_topics(cur, work_id)
            ensure_messages(cur, visitor_id)
        conn.commit()
        print("reader demo data seeded successfully")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


main()
PY
