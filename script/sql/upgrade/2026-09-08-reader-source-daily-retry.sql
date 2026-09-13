-- 书源采集任务每日额度耗尽后的跨日自动重试与运行统计。
-- 该脚本可重复执行，不会覆盖既有任务或运行记录。

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'daily_retry_enabled'),
  'SELECT 1',
  'ALTER TABLE reader_source_task ADD COLUMN daily_retry_enabled CHAR(1) NOT NULL DEFAULT ''1'' COMMENT ''每日额度跨日自动重试：1启用、0停用'' AFTER progress_percent'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'daily_retry_count'),
  'SELECT 1',
  'ALTER TABLE reader_source_task ADD COLUMN daily_retry_count INT NOT NULL DEFAULT 0 COMMENT ''每日额度自动重试累计次数'' AFTER daily_retry_enabled'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'last_daily_retry_date'),
  'SELECT 1',
  'ALTER TABLE reader_source_task ADD COLUMN last_daily_retry_date DATE NULL COMMENT ''最近一次每日额度自动重试日期'' AFTER daily_retry_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'last_daily_retry_at'),
  'SELECT 1',
  'ALTER TABLE reader_source_task ADD COLUMN last_daily_retry_at DATETIME NULL COMMENT ''最近一次每日额度自动重试时间'' AFTER last_daily_retry_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task_run' AND COLUMN_NAME = 'retry_no'),
  'SELECT 1',
  'ALTER TABLE reader_source_task_run ADD COLUMN retry_no INT NOT NULL DEFAULT 0 COMMENT ''每日额度自动重试序号，手工运行是0'' AFTER result_summary'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task_run' AND COLUMN_NAME = 'trigger_type'),
  'SELECT 1',
  'ALTER TABLE reader_source_task_run ADD COLUMN trigger_type VARCHAR(24) NOT NULL DEFAULT ''MANUAL'' COMMENT ''运行触发类型：MANUAL、DAILY_LIMIT'' AFTER retry_no'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task_run' AND COLUMN_NAME = 'trigger_reason'),
  'SELECT 1',
  'ALTER TABLE reader_source_task_run ADD COLUMN trigger_reason VARCHAR(255) NULL COMMENT ''运行触发原因'' AFTER trigger_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE reader_source_task
SET daily_retry_enabled = '1'
WHERE daily_retry_enabled IS NULL OR daily_retry_enabled = '';

UPDATE reader_source_task
SET daily_retry_count = 0
WHERE daily_retry_count IS NULL;

UPDATE reader_source_task_run
SET trigger_type = 'MANUAL'
WHERE trigger_type IS NULL OR trigger_type = '';
