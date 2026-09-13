-- 书源作品连载状态采集。可重复执行。
SET @serial_status_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'reader_source_task_book'
    AND COLUMN_NAME = 'serial_status'
);
SET @serial_status_sql := IF(
  @serial_status_exists = 0,
  'ALTER TABLE reader_source_task_book ADD COLUMN serial_status VARCHAR(16) NOT NULL DEFAULT ''ONGOING'' COMMENT ''来源连载状态：ONGOING、FINISHED'' AFTER category_name',
  'SELECT 1'
);
PREPARE stmt FROM @serial_status_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE reader_source_task_book
SET serial_status = 'ONGOING'
WHERE serial_status IS NULL OR serial_status NOT IN ('ONGOING', 'FINISHED');
