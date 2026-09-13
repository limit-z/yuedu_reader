-- 修复授权书源采集的选择器和历史失联数据；可重复执行。
-- 不放宽站点访问策略，仅修复解析与任务状态。

UPDATE reader_source_rule
SET selector_json = '{"bookList":{"item":"#newscontent .r li","title":".s2 a","author":".s5"},"detail":{"author":"meta[name=author]","authorAttr":"content","category":"meta[property=''og:novel:category'']","categoryAttr":"content","serialStatus":"meta[property=''og:novel:status'']","serialStatusAttr":"content"},"catalog":{"item":"#list dl dd a[href^=''/'']","title":"a","url":"a"},"chapter":{"title":".bookname h1","content":"#content"}}',
    update_time = NOW()
WHERE site_id = (SELECT id FROM reader_source_site WHERE allowed_host = 'www.bookcheng8.com' LIMIT 1)
  AND version_no = 2;

UPDATE reader_source_rule
SET selector_json = REPLACE(REPLACE(selector_json,
    'meta[property=og:novel:category]', 'meta[property=''og:novel:category'']'),
    'meta[property=og:novel:status]', 'meta[property=''og:novel:status'']'),
    update_time = NOW()
WHERE site_id = (SELECT id FROM reader_source_site WHERE allowed_host = 'www.bookcheng8.com' LIMIT 1);

-- 旧 Worker 可能在失败后留下 RUNNING 明细，后端启动后的租约回收会继续处理这些行。
UPDATE reader_source_task_book
SET status = 'QUEUED', started_at = NULL,
    last_error = COALESCE(last_error, '历史 Worker 运行已回收，等待续采')
WHERE status = 'RUNNING'
  AND run_id IN (
    SELECT id FROM reader_source_task_run
    WHERE status = 'RUNNING'
      AND heartbeat_at < DATE_SUB(NOW(), INTERVAL 3 MINUTE)
  );
