-- bookcheng8 的目录 URL 使用连续章节序号，但部分公开标题编号存在错位和重复。
-- 显式按 URL 末段编号，避免通用 Worker 因错误标题重排或覆盖章节。
-- 本脚本幂等，可重复执行。

UPDATE reader_source_rule
SET selector_json = JSON_SET(selector_json, '$.catalog.chapterNoFromUrl', TRUE),
    remark = CONCAT(
        REGEXP_REPLACE(COALESCE(remark, ''), ' ?目录章节按 URL 连续序号解析。', ''),
        ' 目录章节按 URL 连续序号解析。'
    ),
    update_time = NOW()
WHERE site_id = (
    SELECT id FROM reader_source_site
    WHERE allowed_host = 'www.bookcheng8.com'
    LIMIT 1
)
  AND status = '1';
