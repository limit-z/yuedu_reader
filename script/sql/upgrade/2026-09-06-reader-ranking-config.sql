-- 阅读器榜单配置升级
-- AUTO 按系统规则生成榜单，MANUAL 按 reader_ranking_work 的顺序编排作品。

CREATE TABLE IF NOT EXISTS reader_ranking (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '榜单配置ID',
  ranking_key VARCHAR(64) NOT NULL COMMENT 'APP榜单稳定标识',
  ranking_name VARCHAR(64) NOT NULL COMMENT '榜单名称',
  ranking_desc VARCHAR(255) NULL COMMENT '榜单说明',
  ranking_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT '榜单模式：AUTO自动、MANUAL手工',
  sort_rule VARCHAR(16) NOT NULL DEFAULT 'UPDATE' COMMENT '自动规则：HOT、RISING、COMPLETED、NEW、UPDATE',
  sort_no INT NOT NULL DEFAULT 99 COMMENT '展示顺序',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_ranking_key (ranking_key),
  KEY idx_reader_ranking_status_sort (status, sort_no, id)
) COMMENT='阅读器榜单配置表';

CREATE TABLE IF NOT EXISTS reader_ranking_work (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '榜单作品关系ID',
  ranking_id BIGINT NOT NULL COMMENT '榜单配置ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  sort_no INT NOT NULL DEFAULT 99 COMMENT '手工展示顺序',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_ranking_work (ranking_id, work_id),
  KEY idx_reader_ranking_work_sort (ranking_id, status, sort_no, id)
) COMMENT='阅读器榜单手工作品表';

INSERT IGNORE INTO reader_ranking
  (ranking_key, ranking_name, ranking_desc, ranking_mode, sort_rule, sort_no, status, create_time)
VALUES
  ('hot', '畅销榜', '按作品章节规模和更新活跃度生成', 'AUTO', 'HOT', 1, '1', NOW()),
  ('rising', '飙升榜', '按近期更新活跃度生成', 'AUTO', 'RISING', 2, '1', NOW()),
  ('completed', '完结榜', '优先展示已完结作品', 'AUTO', 'COMPLETED', 3, '1', NOW()),
  ('new', '新书榜', '按最近上架时间生成', 'AUTO', 'NEW', 4, '1', NOW());

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
SELECT 1761400000000000017, '榜单管理', 1761400000000000010, 7, 'ranking', 'reader-admin/ranking/index', '', 'N', 'Y', 'C', '0', '0', 'reader:ranking:list', 'trend-charts', 1761000000000000103, 1761100000000000001, NOW(), '阅读器榜单管理菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000017);
