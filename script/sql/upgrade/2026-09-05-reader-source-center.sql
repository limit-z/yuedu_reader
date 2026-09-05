-- 书源采集中心基础表
-- 说明：仅保存授权站点、声明式解析规则、访问策略和可审计任务状态。
-- 采集结果仍须进入阅读器作品/章节/审核链路，不允许 worker 直接写业务表。

CREATE TABLE IF NOT EXISTS reader_source_site (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '书源站点ID',
  site_name VARCHAR(128) NOT NULL COMMENT '站点名称',
  base_url VARCHAR(500) NOT NULL COMMENT '站点根地址',
  allowed_host VARCHAR(255) NOT NULL COMMENT '允许访问的主机名',
  authorization_note VARCHAR(1000) NULL COMMENT '授权或公开许可说明',
  compliance_status VARCHAR(32) NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '合规状态：UNCONFIRMED未确认、APPROVED已确认、REJECTED不允许',
  compliance_checked_at DATETIME NULL COMMENT '合规检查时间',
  compliance_checked_by BIGINT NULL COMMENT '合规检查管理员',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态：0停用、1启用',
  default_policy_id BIGINT NULL COMMENT '默认访问策略ID',
  remark VARCHAR(1000) NULL COMMENT '备注',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_source_site_host (allowed_host),
  KEY idx_reader_source_site_status (status, compliance_status)
) COMMENT='阅读器书源站点表';

CREATE TABLE IF NOT EXISTS reader_source_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '访问策略ID',
  policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
  concurrency_limit INT NOT NULL DEFAULT 1 COMMENT '单站点并发上限',
  min_delay_ms INT NOT NULL DEFAULT 3000 COMMENT '最小请求间隔毫秒',
  max_delay_ms INT NOT NULL DEFAULT 8000 COMMENT '最大请求间隔毫秒',
  requests_per_minute INT NOT NULL DEFAULT 10 COMMENT '每分钟请求上限',
  daily_request_limit INT NOT NULL DEFAULT 1000 COMMENT '每日请求上限',
  connect_timeout_ms INT NOT NULL DEFAULT 10000 COMMENT '连接超时毫秒',
  read_timeout_ms INT NOT NULL DEFAULT 20000 COMMENT '读取超时毫秒',
  max_retries INT NOT NULL DEFAULT 2 COMMENT '最大重试次数',
  circuit_breaker_threshold INT NOT NULL DEFAULT 5 COMMENT '连续失败熔断阈值',
  honor_retry_after CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否遵守 Retry-After：1是、0否',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：0停用、1启用',
  remark VARCHAR(1000) NULL COMMENT '策略说明',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  KEY idx_reader_source_policy_status (status)
) COMMENT='阅读器书源访问策略表';

CREATE TABLE IF NOT EXISTS reader_source_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '解析规则ID',
  site_id BIGINT NOT NULL COMMENT '所属书源站点ID',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  version_no INT NOT NULL DEFAULT 1 COMMENT '规则版本号',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态：0草稿、1启用、2停用',
  search_url_template VARCHAR(1000) NULL COMMENT '搜索地址模板',
  detail_url_template VARCHAR(1000) NULL COMMENT '详情地址模板',
  catalog_url_template VARCHAR(1000) NULL COMMENT '目录地址模板',
  chapter_url_template VARCHAR(1000) NULL COMMENT '章节地址模板',
  selector_json TEXT NOT NULL COMMENT '声明式 CSS/XPath/JSONPath 选择器 JSON',
  test_url VARCHAR(1000) NULL COMMENT '规则测试地址',
  remark VARCHAR(1000) NULL COMMENT '规则说明',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_source_rule_version (site_id, version_no),
  KEY idx_reader_source_rule_site_status (site_id, status)
) COMMENT='阅读器书源解析规则表';

CREATE TABLE IF NOT EXISTS reader_source_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集任务ID',
  task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
  site_id BIGINT NOT NULL COMMENT '书源站点ID',
  rule_id BIGINT NOT NULL COMMENT '固定解析规则版本ID',
  policy_id BIGINT NOT NULL COMMENT '固定访问策略ID',
  executor_type VARCHAR(16) NOT NULL DEFAULT 'JAVA' COMMENT '执行器：JAVA、PYTHON、GO',
  source_work_url VARCHAR(1000) NOT NULL COMMENT '来源作品地址',
  source_work_title VARCHAR(255) NULL COMMENT '来源作品标题',
  start_chapter_no INT NULL COMMENT '起始章节序号',
  end_chapter_no INT NULL COMMENT '结束章节序号',
  incremental CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否增量采集：1是、0否',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、READY、RUNNING、PAUSED、WAITING_REVIEW、COMPLETED、FAILED、CANCELED',
  current_chapter_no INT NULL COMMENT '当前章节游标',
  planned_chapter_count INT NULL COMMENT '计划章节数',
  last_run_at DATETIME NULL COMMENT '最近运行时间',
  fail_reason VARCHAR(1000) NULL COMMENT '失败原因',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  KEY idx_reader_source_task_status (status, update_time),
  KEY idx_reader_source_task_site (site_id, status)
) COMMENT='阅读器书源采集任务表';

CREATE TABLE IF NOT EXISTS reader_source_task_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务运行记录ID',
  task_id BIGINT NOT NULL COMMENT '采集任务ID',
  run_token VARCHAR(128) NOT NULL COMMENT '一次性运行令牌',
  executor_type VARCHAR(16) NOT NULL COMMENT '执行器类型：JAVA、PYTHON、GO',
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '运行状态：RUNNING、PAUSED、COMPLETED、FAILED、CANCELED',
  started_at DATETIME NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  heartbeat_at DATETIME NULL COMMENT '最近心跳时间',
  request_count INT NOT NULL DEFAULT 0 COMMENT '请求总数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数',
  skipped_count INT NOT NULL DEFAULT 0 COMMENT '跳过数',
  failure_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  too_many_requests_count INT NOT NULL DEFAULT 0 COMMENT '429次数',
  circuit_open CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否熔断：1是、0否',
  error_message VARCHAR(2000) NULL COMMENT '脱敏错误摘要',
  result_summary TEXT NULL COMMENT '结果摘要 JSON',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL,
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_source_run_token (run_token),
  KEY idx_reader_source_run_task (task_id, create_time),
  KEY idx_reader_source_run_status (status, heartbeat_at)
) COMMENT='阅读器书源采集运行记录表';

CREATE TABLE IF NOT EXISTS reader_source_chapter_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节快照ID',
  task_id BIGINT NOT NULL COMMENT '采集任务ID',
  source_chapter_id VARCHAR(255) NOT NULL COMMENT '来源章节标识',
  source_url VARCHAR(1000) NOT NULL COMMENT '来源章节地址',
  chapter_no INT NULL COMMENT '章节序号',
  chapter_name VARCHAR(255) NULL COMMENT '章节名称',
  content_hash VARCHAR(128) NOT NULL COMMENT '正文哈希',
  title_hash VARCHAR(128) NULL COMMENT '标题哈希',
  content TEXT NULL COMMENT '差异预览正文，完整正文进入业务内容表',
  snapshot_status VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW、UNCHANGED、CHANGED、CONFIRMED',
  captured_at DATETIME NULL COMMENT '抓取时间',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_reader_snapshot_task_chapter_hash (task_id, source_chapter_id, content_hash),
  KEY idx_reader_snapshot_task_chapter (task_id, chapter_no)
) COMMENT='阅读器书源章节快照表';

CREATE TABLE IF NOT EXISTS reader_source_error (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集错误ID',
  task_id BIGINT NOT NULL COMMENT '采集任务ID',
  run_id BIGINT NULL COMMENT '运行记录ID',
  error_type VARCHAR(32) NOT NULL COMMENT '错误类型：HTTP、TIMEOUT、PARSE、POLICY、SSRF、QUALITY、UNKNOWN',
  http_status INT NULL COMMENT 'HTTP状态码',
  source_url VARCHAR(1000) NULL COMMENT '脱敏来源地址',
  message VARCHAR(2000) NOT NULL COMMENT '脱敏错误摘要',
  retry_at DATETIME NULL COMMENT '计划重试时间',
  resolved CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否已处理：1是、0否',
  create_dept BIGINT(20) NULL,
  create_by BIGINT(20) NULL,
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL,
  update_time DATETIME NULL,
  KEY idx_reader_source_error_task (task_id, resolved, create_time),
  KEY idx_reader_source_error_retry (retry_at, resolved)
) COMMENT='阅读器书源采集错误表';

-- 管理端入口：重复执行升级脚本不会重复创建菜单或管理员角色关联。
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000015000, '书源采集中心', 1761400000000000010, 5, 'source-center',
       'reader-admin/source-center/index', '', 'N', 'Y', 'C', '0', '0', 'reader:source:list',
       'connection', 1761000000000000103, 1761100000000000001, sysdate(), NULL, NULL, '书源采集中心菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000015000);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, 1761400000000015000
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu
  WHERE role_id = 1761300000000000003 AND menu_id = 1761400000000015000
);
