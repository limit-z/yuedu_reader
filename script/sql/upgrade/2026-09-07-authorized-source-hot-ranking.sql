-- 授权书源分类热门榜单采集配置。可重复执行。
-- bookcheng8 已确认授权；另外两个站点仅登记为停用待授权，不创建规则或任务。

INSERT INTO reader_source_site (
  site_name, base_url, allowed_host, authorization_note, compliance_status,
  compliance_checked_at, status, remark, create_time, update_time
)
SELECT 'dushuwo', 'https://m.dushuwo.net/', 'm.dushuwo.net', NULL, 'UNCONFIRMED',
       NULL, '0', '待补充采集授权后再配置规则和任务', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM reader_source_site WHERE allowed_host = 'm.dushuwo.net'
);

INSERT INTO reader_source_site (
  site_name, base_url, allowed_host, authorization_note, compliance_status,
  compliance_checked_at, status, remark, create_time, update_time
)
SELECT 'bqg5555', 'https://www.bqg5555.cc/', 'www.bqg5555.cc', NULL, 'UNCONFIRMED',
       NULL, '0', '待补充采集授权后再配置规则和任务', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM reader_source_site WHERE allowed_host = 'www.bqg5555.cc'
);

SET @bookcheng8_site_id := (
  SELECT id FROM reader_source_site WHERE allowed_host = 'www.bookcheng8.com' LIMIT 1
);

UPDATE reader_source_rule
SET status = '2', update_time = NOW()
WHERE site_id = @bookcheng8_site_id AND version_no = 1 AND status = '1';

INSERT INTO reader_source_rule (
  site_id, rule_name, version_no, status, search_url_template,
  detail_url_template, catalog_url_template, chapter_url_template,
  selector_json, test_url, remark, create_time, update_time
)
VALUES (
  @bookcheng8_site_id,
  'bookcheng8-分类热门与连载状态规则',
  2,
  '1',
  NULL,
  '{url}',
  '{url}',
  '{chapterUrl}',
       '{"bookList":{"item":"#newscontent .r li","title":".s2 a","author":".s5"},"detail":{"author":"meta[name=author]","authorAttr":"content","category":"meta[property=''og:novel:category'']","categoryAttr":"content","serialStatus":"meta[property=''og:novel:status'']","serialStatusAttr":"content"},"catalog":{"item":"#list dl dd a[href^=''/'']","title":"a","url":"a"},"chapter":{"title":".bookname h1","content":"#content"}}',
  'https://www.bookcheng8.com/xuanhuan/1.html',
  '分类页右栏推荐列表；详情页补充作者、分类、连载状态；目录和章节地址均取页面链接',
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  rule_name = VALUES(rule_name),
  status = VALUES(status),
  detail_url_template = VALUES(detail_url_template),
  catalog_url_template = VALUES(catalog_url_template),
  chapter_url_template = VALUES(chapter_url_template),
  selector_json = VALUES(selector_json),
  test_url = VALUES(test_url),
  remark = VALUES(remark),
  update_time = NOW();

SET @bookcheng8_rule_id := (
  SELECT id FROM reader_source_rule
  WHERE site_id = @bookcheng8_site_id AND version_no = 2
  LIMIT 1
);
SET @bookcheng8_policy_id := (
  SELECT default_policy_id FROM reader_source_site WHERE id = @bookcheng8_site_id
);

INSERT INTO reader_source_task (
  task_name, site_id, rule_id, policy_id, executor_type,
  source_work_url, source_work_title, start_chapter_no, end_chapter_no,
  incremental, status, current_chapter_no, planned_chapter_count,
  collection_mode, category_name, total_books, processed_books,
  success_books, skipped_books, failed_books, progress_percent, book_limit,
  create_time, update_time
)
SELECT CONCAT('bookcheng8-', category_name, '-热门前10全章节'),
       @bookcheng8_site_id, @bookcheng8_rule_id, @bookcheng8_policy_id, 'PYTHON',
       source_url, CONCAT(category_name, '热门榜单前十'), 1, NULL,
       '1', 'DRAFT', 0, NULL,
       'CATEGORY', category_name, 0, 0,
       0, 0, 0, 0, 10,
       NOW(), NOW()
FROM (
  SELECT '玄幻魔法' AS category_name, 'https://www.bookcheng8.com/xuanhuan/1.html' AS source_url
  UNION ALL SELECT '言情小说', 'https://www.bookcheng8.com/yanqing/1.html'
  UNION ALL SELECT '仙侠修真', 'https://www.bookcheng8.com/xianxia/1.html'
  UNION ALL SELECT '都市小说', 'https://www.bookcheng8.com/dushi/1.html'
  UNION ALL SELECT '历史小说', 'https://www.bookcheng8.com/lishi/1.html'
  UNION ALL SELECT '网游小说', 'https://www.bookcheng8.com/wangyou/1.html'
  UNION ALL SELECT '竞技小说', 'https://www.bookcheng8.com/jingji/1.html'
  UNION ALL SELECT '科幻小说', 'https://www.bookcheng8.com/kehuan/1.html'
) requested
WHERE NOT EXISTS (
  SELECT 1
  FROM reader_source_task existing
  WHERE existing.task_name = CONCAT('bookcheng8-', requested.category_name, '-热门前10全章节')
);
