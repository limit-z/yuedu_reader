-- 异步小说封面采集任务、候选图片及来源追溯。
-- 依赖 reader_work、reader_source_task、reader_source_task_book 和 sys_oss。

CREATE TABLE IF NOT EXISTS reader_cover_crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '封面采集任务ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  source_task_id BIGINT NULL COMMENT '触发封面的章节采集任务ID',
  source_task_book_id BIGINT NULL COMMENT '触发封面的任务书籍明细ID',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、COMPLETED、FAILED',
  portrait_target_count INT NOT NULL DEFAULT 3 COMMENT '竖版目标数量',
  landscape_target_count INT NOT NULL DEFAULT 3 COMMENT '横版目标数量',
  portrait_success_count INT NOT NULL DEFAULT 0 COMMENT '竖版成功数量',
  landscape_success_count INT NOT NULL DEFAULT 0 COMMENT '横版成功数量',
  current_provider VARCHAR(64) NULL COMMENT '当前来源',
  progress_percent INT NOT NULL DEFAULT 0 COMMENT '任务进度百分比',
  last_error VARCHAR(2000) NULL COMMENT '最近一次错误',
  started_at DATETIME NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_cover_crawl_work_source (work_id, source_task_id, source_task_book_id),
  KEY idx_reader_cover_crawl_status (status, update_time),
  KEY idx_reader_cover_crawl_work (work_id)
) COMMENT='阅读器异步封面采集任务';

CREATE TABLE IF NOT EXISTS reader_cover_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选封面ID',
  crawl_task_id BIGINT NOT NULL COMMENT '封面采集任务ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  orientation VARCHAR(16) NOT NULL COMMENT '方向：PORTRAIT、LANDSCAPE',
  source_provider VARCHAR(64) NOT NULL COMMENT '来源名称',
  source_query VARCHAR(512) NOT NULL COMMENT '来源检索词：书名 + 作者',
  source_page_url VARCHAR(1000) NULL COMMENT '来源页面地址',
  source_image_url VARCHAR(1000) NOT NULL COMMENT '原始图片地址',
  uploaded_oss_id BIGINT NULL COMMENT '上传后的OSS ID',
  stored_image_url VARCHAR(1000) NULL COMMENT '站内图片地址',
  width INT NULL COMMENT '图片宽度',
  height INT NULL COMMENT '图片高度',
  content_hash CHAR(64) NULL COMMENT '图片内容SHA-256',
  status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS、FAILED',
  failure_reason VARCHAR(1000) NULL COMMENT '失败原因',
  captured_at DATETIME NULL COMMENT '采集时间',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_cover_candidate_hash (work_id, orientation, content_hash),
  KEY idx_reader_cover_candidate_task (crawl_task_id, orientation, status),
  KEY idx_reader_cover_candidate_work (work_id, orientation, status)
) COMMENT='阅读器封面候选图片及来源';
