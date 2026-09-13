-- Distinguish a queued run from a run that was actually claimed by a Worker.
-- Safe to execute repeatedly on an existing installation.
SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task_run'
           AND COLUMN_NAME = 'claimed_at'),
  'SELECT 1',
  'ALTER TABLE reader_source_task_run ADD COLUMN claimed_at DATETIME NULL COMMENT ''Worker首次成功领取时间；为空表示等待领取'' AFTER heartbeat_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
