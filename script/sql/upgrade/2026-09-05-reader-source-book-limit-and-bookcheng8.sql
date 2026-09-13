-- 书源批量数量限制与 bookcheng8 规则修正
-- 可重复执行；用于已执行过批量采集基础迁移的开发库。

SET @book_limit_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'book_limit'
);
SET @book_limit_sql := IF(
  @book_limit_exists = 0,
  'ALTER TABLE reader_source_task ADD COLUMN book_limit INT NOT NULL DEFAULT 1 COMMENT ''当前任务最多采集的书籍数量，单本任务固定为1''',
  'SELECT 1'
);
PREPARE stmt FROM @book_limit_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE reader_source_task
SET book_limit = 1
WHERE book_limit IS NULL OR book_limit < 1;

UPDATE reader_source_rule
SET catalog_url_template = '{url}',
    chapter_url_template = '/{workId}/{chapterNo}.html',
    selector_json = '{"bookList":{"item":"#newscontent .l li","title":".s2 a","author":".s4","category":".s1","latestChapterNo":".s3 a"},"catalog":{"item":"#list dl dd","title":"a","url":"a"},"chapter":{"title":".bookname h1","content":"#content"}}',
    test_url = 'https://www.bookcheng8.com/5_5631/1.html',
    remark = 'bookcheng8：GBK 页面；书籍列表 #newscontent .l li，作品地址 /{workId}/，章节地址 /{workId}/{chapterNo}.html，目录 #list，正文 #content。'
WHERE site_id = (SELECT id FROM reader_source_site WHERE allowed_host = 'www.bookcheng8.com' LIMIT 1)
  AND rule_name = 'bookcheng8-站点解析规则';
