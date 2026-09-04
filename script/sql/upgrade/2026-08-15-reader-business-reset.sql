-- 阅读器业务数据重置脚本
-- 适用时间：2026-08-15
-- 作用：清空阅读器作品、章节、审核、导入任务、书架、历史、进度等业务数据，便于重新导入验证
-- 说明：
-- 1. 本脚本只清空 reader_* 业务表，不删除 sys_user、sys_menu、sys_config 等系统表
-- 2. 本脚本默认不删除 sys_oss 中的上传文件记录，也不删除 OSS/MinIO 中的物理文件
-- 3. 如果你希望连上传文件记录一起清掉，请在确认后额外处理 sys_oss 与对象存储

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE reader_bookshelf;
TRUNCATE TABLE reader_reading_history;
TRUNCATE TABLE reader_reading_progress;

TRUNCATE TABLE reader_content_audit;
TRUNCATE TABLE reader_import_file;
TRUNCATE TABLE reader_import_task;

TRUNCATE TABLE reader_comic_page;
TRUNCATE TABLE reader_comic_chapter;

TRUNCATE TABLE reader_novel_chapter_content;
TRUNCATE TABLE reader_novel_chapter;

TRUNCATE TABLE reader_work;

SET FOREIGN_KEY_CHECKS = 1;

-- 执行后可用以下 SQL 快速确认是否已清空
-- SELECT 'reader_work' AS table_name, COUNT(*) AS cnt FROM reader_work
-- UNION ALL
-- SELECT 'reader_novel_chapter', COUNT(*) FROM reader_novel_chapter
-- UNION ALL
-- SELECT 'reader_novel_chapter_content', COUNT(*) FROM reader_novel_chapter_content
-- UNION ALL
-- SELECT 'reader_comic_chapter', COUNT(*) FROM reader_comic_chapter
-- UNION ALL
-- SELECT 'reader_comic_page', COUNT(*) FROM reader_comic_page
-- UNION ALL
-- SELECT 'reader_import_task', COUNT(*) FROM reader_import_task
-- UNION ALL
-- SELECT 'reader_import_file', COUNT(*) FROM reader_import_file
-- UNION ALL
-- SELECT 'reader_content_audit', COUNT(*) FROM reader_content_audit
-- UNION ALL
-- SELECT 'reader_bookshelf', COUNT(*) FROM reader_bookshelf
-- UNION ALL
-- SELECT 'reader_reading_history', COUNT(*) FROM reader_reading_history
-- UNION ALL
-- SELECT 'reader_reading_progress', COUNT(*) FROM reader_reading_progress;
