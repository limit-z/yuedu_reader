-- 已授权书源 dushuwo / bqg5555 的分类热门前十全章节任务。
-- 仅在站点处于 APPROVED 且启用时创建策略、规则和任务，可重复执行。

INSERT INTO reader_source_policy (
  policy_name, concurrency_limit, min_delay_ms, max_delay_ms,
  requests_per_minute, daily_request_limit, connect_timeout_ms, read_timeout_ms,
  max_retries, circuit_breaker_threshold, honor_retry_after, status, remark,
  create_time, update_time
)
SELECT 'dushuwo-授权低频采集', 1, 15000, 30000, 2, 1000, 15000, 30000,
       1, 5, '1', '1', '授权内容采集；单站点串行、遵守 robots.txt 与 Retry-After。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reader_source_policy WHERE policy_name = 'dushuwo-授权低频采集');

INSERT INTO reader_source_policy (
  policy_name, concurrency_limit, min_delay_ms, max_delay_ms,
  requests_per_minute, daily_request_limit, connect_timeout_ms, read_timeout_ms,
  max_retries, circuit_breaker_threshold, honor_retry_after, status, remark,
  create_time, update_time
)
SELECT 'bqg5555-授权低频采集', 1, 15000, 30000, 2, 1000, 15000, 30000,
       1, 5, '1', '1', '授权内容采集；单站点串行、遵守 robots.txt 与 Retry-After。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reader_source_policy WHERE policy_name = 'bqg5555-授权低频采集');

SET @dushuwo_site_id := (
  SELECT id FROM reader_source_site
  WHERE allowed_host = 'm.dushuwo.net' AND compliance_status = 'APPROVED' AND status = '1'
  LIMIT 1
);
SET @bqg5555_site_id := (
  SELECT id FROM reader_source_site
  WHERE allowed_host = 'www.bqg5555.cc' AND compliance_status = 'APPROVED' AND status = '1'
  LIMIT 1
);
SET @dushuwo_policy_id := (SELECT id FROM reader_source_policy WHERE policy_name = 'dushuwo-授权低频采集' LIMIT 1);
SET @bqg5555_policy_id := (SELECT id FROM reader_source_policy WHERE policy_name = 'bqg5555-授权低频采集' LIMIT 1);

UPDATE reader_source_site SET default_policy_id = @dushuwo_policy_id, update_time = NOW()
WHERE id = @dushuwo_site_id;
UPDATE reader_source_site SET default_policy_id = @bqg5555_policy_id, update_time = NOW()
WHERE id = @bqg5555_site_id;

INSERT INTO reader_source_rule (
  site_id, rule_name, version_no, status, detail_url_template,
  catalog_url_template, chapter_url_template, selector_json, test_url, remark,
  create_time, update_time
)
SELECT @dushuwo_site_id, 'dushuwo-分类推荐与连载状态规则', 1, '1', '{url}', '{url}', '{chapterUrl}',
       '{"bookList":{"item":".branch_menu > .article","title":".content h6 a","author":".content .author"},"detail":{"author":".cataloginfo .infotype p:first-child a","serialStatus":".info_menu1 .list_xm"},"catalog":{"item":".info_menu1 .list_xm li","title":"a"},"chapter":{"title":"#chaptertitle","content":"#novelcontent"}}',
       'https://m.dushuwo.net/xuanhuan/1.html',
       '分类页按站点推荐顺序取前十；详情无显式状态时按连载，目录出现大结局时识别为完结。', NOW(), NOW()
WHERE @dushuwo_site_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM reader_source_rule WHERE site_id = @dushuwo_site_id AND version_no = 1);

INSERT INTO reader_source_rule (
  site_id, rule_name, version_no, status, detail_url_template,
  catalog_url_template, chapter_url_template, selector_json, test_url, remark,
  create_time, update_time
)
SELECT @bqg5555_site_id, 'bqg5555-分类热门与拆页章节规则', 1, '1', '{url}', '{url}', '{chapterUrl}',
       '{"bookList":{"item":".layout-col3 .item, .layout2.layout-col2 .txt-list-row5 li","title":"dt a, .s2 a","author":"dt span, .s4"},"detail":{"serialStatus":".detail-box .info .fix p:nth-of-type(3)"},"catalog":{"item":".section-box .section-list li","title":"a"},"chapter":{"title":"h1.title","content":"#content","nextPage":"#content a[rel=next]","nextChapter":"#next_url","nextChapterText":"下一章","maxPages":10}}',
       'https://www.bqg5555.cc/list/1/1/',
       '分类推荐区优先，不足十本时按最近更新补足；同一章节拆页通过 rel=next 合并。', NOW(), NOW()
WHERE @bqg5555_site_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM reader_source_rule WHERE site_id = @bqg5555_site_id AND version_no = 1);

SET @dushuwo_rule_id := (SELECT id FROM reader_source_rule WHERE site_id = @dushuwo_site_id AND version_no = 1 LIMIT 1);
SET @bqg5555_rule_id := (SELECT id FROM reader_source_rule WHERE site_id = @bqg5555_site_id AND version_no = 1 LIMIT 1);

INSERT INTO reader_source_task (
  task_name, site_id, rule_id, policy_id, executor_type, source_work_url,
  start_chapter_no, end_chapter_no, incremental, status, collection_mode,
  category_name, book_limit, total_books, processed_books, success_books,
  skipped_books, failed_books, progress_percent, create_time, update_time
)
SELECT CONCAT('dushuwo-', requested.category_name, '-热门前10全章节'),
       @dushuwo_site_id, @dushuwo_rule_id, @dushuwo_policy_id, 'PYTHON', requested.source_url,
       1, NULL, '1', 'DRAFT', 'CATEGORY', requested.category_name, 10,
       0, 0, 0, 0, 0, 0, NOW(), NOW()
FROM (
  SELECT '玄幻魔法' category_name, 'https://m.dushuwo.net/xuanhuan/1.html' source_url
  UNION ALL SELECT '言情小说', 'https://m.dushuwo.net/yanqing/1.html'
  UNION ALL SELECT '仙侠修真', 'https://m.dushuwo.net/xianxia/1.html'
  UNION ALL SELECT '都市小说', 'https://m.dushuwo.net/dushi/1.html'
  UNION ALL SELECT '历史小说', 'https://m.dushuwo.net/lishi/1.html'
  UNION ALL SELECT '网游小说', 'https://m.dushuwo.net/wangyou/1.html'
  UNION ALL SELECT '竞技小说', 'https://m.dushuwo.net/jingji/1.html'
  UNION ALL SELECT '科幻小说', 'https://m.dushuwo.net/kehuan/1.html'
) requested
WHERE @dushuwo_site_id IS NOT NULL AND @dushuwo_rule_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM reader_source_task existing
    WHERE existing.task_name = CONCAT('dushuwo-', requested.category_name, '-热门前10全章节')
  );

INSERT INTO reader_source_task (
  task_name, site_id, rule_id, policy_id, executor_type, source_work_url,
  start_chapter_no, end_chapter_no, incremental, status, collection_mode,
  category_name, book_limit, total_books, processed_books, success_books,
  skipped_books, failed_books, progress_percent, create_time, update_time
)
SELECT CONCAT('bqg5555-', requested.category_name, '-热门前10全章节'),
       @bqg5555_site_id, @bqg5555_rule_id, @bqg5555_policy_id, 'PYTHON', requested.source_url,
       1, NULL, '1', 'DRAFT', 'CATEGORY', requested.category_name, 10,
       0, 0, 0, 0, 0, 0, NOW(), NOW()
FROM (
  SELECT '玄幻魔法' category_name, 'https://www.bqg5555.cc/list/1/1/' source_url
  UNION ALL SELECT '武侠修真', 'https://www.bqg5555.cc/list/2/1/'
  UNION ALL SELECT '都市言情', 'https://www.bqg5555.cc/list/3/1/'
  UNION ALL SELECT '历史军事', 'https://www.bqg5555.cc/list/4/1/'
  UNION ALL SELECT '科幻灵异', 'https://www.bqg5555.cc/list/5/1/'
  UNION ALL SELECT '游戏竞技', 'https://www.bqg5555.cc/list/6/1/'
  UNION ALL SELECT '精品小说', 'https://www.bqg5555.cc/list/7/1/'
  UNION ALL SELECT '其他小说', 'https://www.bqg5555.cc/list/8/1/'
) requested
WHERE @bqg5555_site_id IS NOT NULL AND @bqg5555_rule_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM reader_source_task existing
    WHERE existing.task_name = CONCAT('bqg5555-', requested.category_name, '-热门前10全章节')
  );

-- 目录会省略部分 href；使用每章末页公开的“下一章”同站链接补齐空档。
UPDATE reader_source_rule
SET selector_json = JSON_SET(
      selector_json,
      '$.chapter.nextChapter', '#next_url',
      '$.chapter.nextChapterText', '下一章'
    ),
    update_time = NOW()
WHERE site_id = @bqg5555_site_id AND version_no = 1;
