-- 小说章节旧正文列清理脚本
-- 适用时间：2026-08-15 之后
-- 使用前提：reader_novel_chapter_content 已完成迁移，且应用已稳定从正文副表读取内容
-- 注意：本脚本会清空旧表 content 列，属于回收历史冗余数据的操作，请在确认后再执行

-- 第一步：执行前核对是否仍有未迁移正文
SELECT COUNT(*) AS missing_content_rows
FROM reader_novel_chapter chapter
WHERE chapter.content IS NOT NULL
  AND chapter.content <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM reader_novel_chapter_content content
    WHERE content.chapter_id = chapter.id
  );

-- 第二步：确认 missing_content_rows = 0 后，再清空旧列数据
-- UPDATE reader_novel_chapter
-- SET content = NULL
-- WHERE content IS NOT NULL
--   AND content <> '';

-- 第三步：如果已经清空旧列，并希望尽快回收表空间，可执行表重建
-- OPTIMIZE TABLE reader_novel_chapter;

-- 第四步：在应用稳定运行一段时间后，如确认不再需要旧列，可再执行删列
-- ALTER TABLE reader_novel_chapter DROP COLUMN content;
