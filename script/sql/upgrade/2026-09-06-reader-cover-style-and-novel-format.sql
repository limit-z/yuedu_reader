-- 阅读器自动封面背景配置与小说正文段落格式升级
-- Date: 2026-09-06

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_work' AND COLUMN_NAME = 'cover_background_mode'),
  'SELECT 1',
  'ALTER TABLE reader_work ADD COLUMN cover_background_mode VARCHAR(16) NOT NULL DEFAULT ''GLOBAL'' COMMENT ''自动封面背景模式：GLOBAL继承全局、COLOR背景色、IMAGE背景图片'' AFTER cover_landscape_url'
);
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_work' AND COLUMN_NAME = 'cover_background_color'),
  'SELECT 1',
  'ALTER TABLE reader_work ADD COLUMN cover_background_color CHAR(7) NULL COMMENT ''作品级自动封面背景色，格式#RRGGBB'' AFTER cover_background_mode'
);
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_work' AND COLUMN_NAME = 'cover_background_oss_id'),
  'SELECT 1',
  'ALTER TABLE reader_work ADD COLUMN cover_background_oss_id BIGINT NULL COMMENT ''作品级自动封面背景图片OSS ID'' AFTER cover_background_color'
);
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_work' AND COLUMN_NAME = 'cover_revision'),
  'SELECT 1',
  'ALTER TABLE reader_work ADD COLUMN cover_revision INT NOT NULL DEFAULT 0 COMMENT ''自动封面缓存版本号'' AFTER cover_background_oss_id'
);
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;

UPDATE reader_work
SET cover_background_mode = 'GLOBAL'
WHERE cover_background_mode IS NULL OR cover_background_mode = '';

INSERT INTO sys_config (
  config_id, config_name, config_key, config_value, config_type,
  create_dept, create_by, create_time, update_by, update_time, remark
)
SELECT
  1761700000000000004,
  '阅读器-自动封面全局背景',
  'reader.cover.defaultStyle',
  '{"mode":"COLOR","color":"#FFF4F2"}',
  'Y',
  NULL, NULL, NOW(), NULL, NULL,
  '采集建档和自动封面使用的全局背景配置'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_config WHERE config_key = 'reader.cover.defaultStyle'
);
