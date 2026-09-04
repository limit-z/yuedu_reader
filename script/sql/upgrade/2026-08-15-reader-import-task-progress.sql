-- 阅读器导入任务进度字段升级脚本
-- 适用时间：2026-08-15
-- 作用：为 reader_import_task 增加异步解析进度展示所需字段

ALTER TABLE reader_import_task
  ADD COLUMN IF NOT EXISTS total_units INT NOT NULL DEFAULT 0 COMMENT '总处理单元数';

ALTER TABLE reader_import_task
  ADD COLUMN IF NOT EXISTS processed_units INT NOT NULL DEFAULT 0 COMMENT '已处理单元数';

ALTER TABLE reader_import_task
  ADD COLUMN IF NOT EXISTS progress_percent INT NOT NULL DEFAULT 0 COMMENT '进度百分比';

ALTER TABLE reader_import_task
  ADD COLUMN IF NOT EXISTS progress_message VARCHAR(255) NULL COMMENT '进度说明';
