-- 书源采集自动化升级：失败分类、有限重试、备用书源续采和全链路日志。
-- 本脚本只增加字段和表，不删除、不重写既有采集数据，可重复执行。

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'failure_code'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN failure_code VARCHAR(32) NULL COMMENT ''最近一次失败分类：DAILY_LIMIT、HTTP_401、HTTP_403、TIMEOUT、PARSE、QUALITY 等'' AFTER last_run_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'auto_retry_enabled'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN auto_retry_enabled CHAR(1) NOT NULL DEFAULT ''1'' COMMENT ''普通异常自动重试：1启用、0停用'' AFTER failure_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'auto_retry_count'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN auto_retry_count INT NOT NULL DEFAULT 0 COMMENT ''普通异常自动重试累计次数'' AFTER auto_retry_enabled');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'max_auto_retry_count'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN max_auto_retry_count INT NOT NULL DEFAULT 3 COMMENT ''普通异常自动重试最大次数'' AFTER auto_retry_count');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'retry_after'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN retry_after DATETIME NULL COMMENT ''下一次普通异常自动重试时间'' AFTER max_auto_retry_count');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'parent_task_id'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN parent_task_id BIGINT NULL COMMENT ''父采集任务ID；备用书源续采任务使用'' AFTER retry_after');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'fallback_id'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN fallback_id BIGINT NULL COMMENT ''创建本续采任务使用的备用书源路由ID'' AFTER parent_task_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reader_source_task' AND COLUMN_NAME = 'fallback_source_task_book_id'), 'SELECT 1', 'ALTER TABLE reader_source_task ADD COLUMN fallback_source_task_book_id BIGINT NULL COMMENT ''被续采的原任务书籍明细ID'' AFTER fallback_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS reader_source_task_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集任务日志ID',
  task_id BIGINT NOT NULL COMMENT '所属采集任务ID',
  run_id BIGINT NULL COMMENT '所属运行记录ID',
  task_book_id BIGINT NULL COMMENT '所属任务书籍明细ID',
  level VARCHAR(16) NOT NULL DEFAULT 'INFO' COMMENT '日志级别：INFO、WARN、ERROR',
  event_type VARCHAR(24) NOT NULL COMMENT '事件阶段：TASK、DISCOVERY、CLAIM、PERMIT、FETCH、RESULT、RETRY、FALLBACK',
  message VARCHAR(2000) NOT NULL COMMENT '面向管理端的脱敏日志摘要',
  detail_json VARCHAR(4000) NULL COMMENT '结构化扩展信息JSON',
  event_at DATETIME NOT NULL COMMENT '事件发生时间',
  create_dept BIGINT NULL COMMENT '创建部门',
  create_by BIGINT NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_source_task_log_task_event (task_id, event_at),
  KEY idx_reader_source_task_log_run (run_id, event_at),
  KEY idx_reader_source_task_log_book (task_book_id, event_at)
) COMMENT='阅读器书源采集全链路任务日志表';

CREATE TABLE IF NOT EXISTS reader_source_task_fallback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '备用书源路由ID',
  task_id BIGINT NOT NULL COMMENT '主采集任务ID',
  priority INT NOT NULL DEFAULT 1 COMMENT '路由优先级，数字越小越优先',
  site_id BIGINT NOT NULL COMMENT '备用书源站点ID',
  rule_id BIGINT NOT NULL COMMENT '备用书源解析规则ID',
  policy_id BIGINT NOT NULL COMMENT '备用书源访问策略ID',
  source_url_template VARCHAR(1000) NOT NULL COMMENT '续采作品地址模板，支持{title}、{author}',
  auto_enabled CHAR(1) NOT NULL DEFAULT '1' COMMENT '授权失败时是否自动切换：1是、0否',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '路由状态：1启用、0停用',
  last_child_task_id BIGINT NULL COMMENT '最近一次创建的续采子任务ID',
  create_dept BIGINT NULL COMMENT '创建部门',
  create_by BIGINT NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_source_task_fallback_task (task_id, priority),
  KEY idx_reader_source_task_fallback_site (site_id, status)
) COMMENT='阅读器书源采集备用站点路由表';

UPDATE reader_source_task SET auto_retry_enabled = '1' WHERE auto_retry_enabled IS NULL OR auto_retry_enabled = '';
UPDATE reader_source_task SET auto_retry_count = 0 WHERE auto_retry_count IS NULL;
UPDATE reader_source_task SET max_auto_retry_count = 3 WHERE max_auto_retry_count IS NULL OR max_auto_retry_count < 0;
