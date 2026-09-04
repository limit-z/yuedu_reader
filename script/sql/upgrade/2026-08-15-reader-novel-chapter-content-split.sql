-- 小说章节正文分表迁移脚本
-- 适用时间：2026-08-15
-- 作用：把 reader_novel_chapter 中的大正文拆到 reader_novel_chapter_content，减少目录和元数据查询负担

CREATE TABLE IF NOT EXISTS reader_novel_chapter_content (
  chapter_id BIGINT PRIMARY KEY COMMENT '章节ID',
  content LONGTEXT NULL COMMENT '章节正文'
) COMMENT = '小说章节正文表';

ALTER TABLE reader_novel_chapter
  ADD KEY idx_reader_novel_chapter_work_no (work_id, chapter_no, id);

INSERT INTO reader_novel_chapter_content (chapter_id, content)
SELECT chapter.id, chapter.content
FROM reader_novel_chapter chapter
WHERE chapter.content IS NOT NULL
  AND chapter.content <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM reader_novel_chapter_content content
    WHERE content.chapter_id = chapter.id
  );
