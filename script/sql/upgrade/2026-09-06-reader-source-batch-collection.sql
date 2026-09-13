-- 阅读器书源批量采集扩展
-- 前置：已执行 2026-09-05-reader-source-center.sql。
-- 说明：本脚本只扩展批量采集所需字段和表，不修改 sys_user 管理员体系。

ALTER TABLE reader_work
  ADD COLUMN author_name VARCHAR(128) NOT NULL DEFAULT '未知作者' COMMENT '作品作者，用于作品去重与展示',
  ADD COLUMN dedupe_key CHAR(64) NOT NULL DEFAULT '' COMMENT '规范化书名+作者 SHA-256 去重键';

-- 既有作品先回填作者和去重键，再建立唯一索引；执行前请确认历史作品不存在同名同作者重复记录。
UPDATE reader_work
SET author_name = CASE WHEN author_name IS NULL OR TRIM(author_name) = '' THEN '未知作者' ELSE TRIM(author_name) END,
    dedupe_key = SHA2(CONCAT(LOWER(TRIM(title)), CHAR(0), LOWER(TRIM(author_name))), 256)
WHERE dedupe_key = '' OR dedupe_key IS NULL;

ALTER TABLE reader_work
  ADD UNIQUE KEY uk_reader_work_dedupe_key (dedupe_key);

CREATE TABLE IF NOT EXISTS reader_work_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '作品分类ID',
  category_name VARCHAR(64) NOT NULL COMMENT '分类展示名称：玄幻、言情、修仙等',
  normalized_name VARCHAR(64) NOT NULL COMMENT '规范化分类名称，用于唯一判断',
  source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '分类来源：MANUAL手工、SOURCE书源发现',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_work_category_normalized (normalized_name)
) COMMENT='阅读器作品内容分类表';

ALTER TABLE reader_source_task
  ADD COLUMN collection_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '采集模式：SINGLE单本、ALL全站、CATEGORY按分类',
  ADD COLUMN category_name VARCHAR(64) NULL COMMENT '按分类采集时的来源分类名称',
  ADD COLUMN book_limit INT NOT NULL DEFAULT 1 COMMENT '当前任务最多采集的书籍数量，单本任务固定为1',
  ADD COLUMN batch_no VARCHAR(64) NULL COMMENT '当前采集批次号',
  ADD COLUMN total_books INT NOT NULL DEFAULT 0 COMMENT '当前批次发现的书籍总数',
  ADD COLUMN processed_books INT NOT NULL DEFAULT 0 COMMENT '当前批次已处理书籍数',
  ADD COLUMN success_books INT NOT NULL DEFAULT 0 COMMENT '当前批次成功书籍数',
  ADD COLUMN skipped_books INT NOT NULL DEFAULT 0 COMMENT '当前批次跳过书籍数',
  ADD COLUMN failed_books INT NOT NULL DEFAULT 0 COMMENT '当前批次失败书籍数',
  ADD COLUMN progress_percent INT NOT NULL DEFAULT 0 COMMENT '当前批次整体进度百分比';

CREATE TABLE IF NOT EXISTS reader_source_task_book (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务书籍明细ID',
  task_id BIGINT NOT NULL COMMENT '采集任务ID',
  run_id BIGINT NULL COMMENT '运行批次ID',
  batch_no VARCHAR(64) NULL COMMENT '展示批次号',
  source_work_url VARCHAR(1000) NOT NULL COMMENT '来源作品地址',
  source_work_url_hash CHAR(64) GENERATED ALWAYS AS (SHA2(source_work_url, 256)) STORED COMMENT '来源作品地址哈希，用于完整地址去重',
  source_work_title VARCHAR(255) NOT NULL COMMENT '来源作品标题',
  author_name VARCHAR(128) NOT NULL DEFAULT '未知作者' COMMENT '来源作者',
  category_name VARCHAR(64) NULL COMMENT '来源内容分类',
  serial_status VARCHAR(16) NOT NULL DEFAULT 'ONGOING' COMMENT '来源连载状态：ONGOING、FINISHED',
  work_dedupe_key CHAR(64) NOT NULL COMMENT '标题+作者规范化去重键',
  work_id BIGINT NULL COMMENT '复用或新建的本地作品ID',
  dedupe_action VARCHAR(16) NOT NULL COMMENT '去重动作：NEW、REUSE、INCREMENTAL、UNCHANGED',
  status VARCHAR(24) NOT NULL DEFAULT 'DISCOVERED' COMMENT '明细状态：DISCOVERED、QUEUED、RUNNING、WAITING_REVIEW、COMPLETED、FAILED、SKIPPED',
  local_latest_chapter_no INT NOT NULL DEFAULT 0 COMMENT '本地最新章节序号',
  remote_latest_chapter_no INT NULL COMMENT '来源最新章节序号',
  planned_chapter_count INT NOT NULL DEFAULT 0 COMMENT '计划采集章节数',
  processed_chapter_count INT NOT NULL DEFAULT 0 COMMENT '已处理章节数',
  success_chapter_count INT NOT NULL DEFAULT 0 COMMENT '成功章节数',
  skipped_chapter_count INT NOT NULL DEFAULT 0 COMMENT '跳过章节数',
  failure_chapter_count INT NOT NULL DEFAULT 0 COMMENT '失败章节数',
  started_at DATETIME NULL COMMENT '明细开始时间',
  finished_at DATETIME NULL COMMENT '明细结束时间',
  last_error VARCHAR(2000) NULL COMMENT '脱敏后的最后错误',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_source_task_book_url (task_id, source_work_url_hash),
  KEY idx_reader_source_task_book_task_status (task_id, status, update_time),
  KEY idx_reader_source_task_book_work (work_id),
  KEY idx_reader_source_task_book_batch (batch_no, status)
) COMMENT='阅读器批量采集任务书籍明细表';

ALTER TABLE reader_source_chapter_snapshot
  ADD COLUMN task_book_id BIGINT NULL COMMENT '任务书籍明细ID，旧单本快照为空',
  ADD COLUMN work_id BIGINT NULL COMMENT '归属本地作品ID',
  ADD COLUMN run_id BIGINT NULL COMMENT '所属运行批次ID';

-- 兼容历史单本任务：未设置模式的数据默认按 SINGLE 处理。
UPDATE reader_source_task SET collection_mode = 'SINGLE' WHERE collection_mode IS NULL OR collection_mode = '';
UPDATE reader_source_task SET book_limit = 1 WHERE book_limit IS NULL OR book_limit < 1;
