-- 书源发现中心
-- 仅从管理员配置的公开索引、订阅 Feed 或已授权目录获取候选地址。
-- 候选必须经过黑名单、公网地址、robots.txt 和人工审核，不能直接变成可采集站点。

CREATE TABLE IF NOT EXISTS reader_source_discovery_provider (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '发现源ID',
  provider_name VARCHAR(128) NOT NULL COMMENT '发现源名称',
  provider_url VARCHAR(1000) NOT NULL COMMENT '公开索引或授权Feed地址',
  provider_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '协议类型：TEXT、JSON、RSS',
  authorization_note VARCHAR(1000) NOT NULL COMMENT '公开许可或授权说明',
  poll_interval_seconds INT NOT NULL DEFAULT 3600 COMMENT '自动轮询间隔秒数',
  request_interval_ms INT NOT NULL DEFAULT 3000 COMMENT '发现检查请求间隔毫秒',
  max_candidates INT NOT NULL DEFAULT 20 COMMENT '单次最多处理候选数量',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态：0停用、1启用',
  last_run_at DATETIME NULL COMMENT '最近运行时间',
  next_run_at DATETIME NULL COMMENT '下一次计划运行时间',
  last_run_status VARCHAR(32) NULL COMMENT '最近运行状态',
  last_error VARCHAR(1000) NULL COMMENT '最近错误摘要',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_discovery_provider_url (provider_url(768)),
  KEY idx_reader_discovery_provider_schedule (status, next_run_at)
) COMMENT='阅读器书源发现源配置表';

CREATE TABLE IF NOT EXISTS reader_source_discovery_blacklist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '黑名单ID',
  matcher_type VARCHAR(16) NOT NULL COMMENT '匹配类型：HOST、SUFFIX、URL',
  matcher_value VARCHAR(1000) NOT NULL COMMENT '主机后缀或精确地址',
  reason VARCHAR(500) NOT NULL COMMENT '拦截原因',
  source VARCHAR(128) NULL COMMENT '黑名单来源',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：0停用、1启用',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_discovery_blacklist_match (matcher_type, matcher_value(700)),
  KEY idx_reader_discovery_blacklist_status (status)
) COMMENT='阅读器书源发现黑名单表';

CREATE TABLE IF NOT EXISTS reader_source_discovery_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选ID',
  provider_id BIGINT NOT NULL COMMENT '发现源ID',
  candidate_url VARCHAR(1000) NOT NULL COMMENT '候选书源地址',
  candidate_host VARCHAR(255) NOT NULL COMMENT '候选主机',
  candidate_name VARCHAR(255) NULL COMMENT '候选名称',
  discovery_status VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW' COMMENT '状态：NEEDS_REVIEW、BLOCKED、CHECK_FAILED、APPROVED、REJECTED',
  blacklist_status VARCHAR(32) NOT NULL DEFAULT 'NOT_MATCHED' COMMENT '黑名单状态：NOT_MATCHED、MATCHED',
  robots_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'robots状态：NOT_CHECKED、ALLOWED、DISALLOWED、UNAVAILABLE',
  availability_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT '可用性：NOT_CHECKED、AVAILABLE、UNAVAILABLE',
  http_status INT NULL COMMENT '最近检查HTTP状态码',
  check_message VARCHAR(1000) NULL COMMENT '检查摘要',
  last_checked_at DATETIME NULL COMMENT '最近检查时间',
  reviewed_by BIGINT NULL COMMENT '审核管理员',
  reviewed_at DATETIME NULL COMMENT '审核时间',
  site_id BIGINT NULL COMMENT '审核通过后生成的正式站点ID',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_discovery_candidate_url (candidate_url(768)),
  KEY idx_reader_discovery_candidate_review (discovery_status, update_time),
  KEY idx_reader_discovery_candidate_provider (provider_id, create_time)
) COMMENT='阅读器书源发现候选地址表';

CREATE TABLE IF NOT EXISTS reader_source_discovery_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '发现运行ID',
  provider_id BIGINT NOT NULL COMMENT '发现源ID',
  run_token VARCHAR(128) NOT NULL COMMENT '运行令牌',
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '运行状态：RUNNING、COMPLETED、FAILED、SKIPPED',
  started_at DATETIME NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  candidate_count INT NOT NULL DEFAULT 0 COMMENT '发现候选数',
  blocked_count INT NOT NULL DEFAULT 0 COMMENT '黑名单拦截数',
  robots_denied_count INT NOT NULL DEFAULT 0 COMMENT 'robots拒绝数',
  available_count INT NOT NULL DEFAULT 0 COMMENT '可用候选数',
  failed_count INT NOT NULL DEFAULT 0 COMMENT '检查失败数',
  error_message VARCHAR(1000) NULL COMMENT '错误摘要',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_discovery_run_token (run_token),
  KEY idx_reader_discovery_run_provider (provider_id, create_time),
  KEY idx_reader_discovery_run_status (status, started_at)
) COMMENT='阅读器书源发现运行记录表';
