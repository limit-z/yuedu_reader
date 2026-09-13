-- 书源采集结果建档与内容审核关联
ALTER TABLE reader_content_audit
  ADD COLUMN source_task_id BIGINT NULL COMMENT '关联书源采集任务ID',
  ADD COLUMN source_task_book_id BIGINT NULL COMMENT '关联书源采集任务书籍明细ID';

ALTER TABLE reader_content_audit
  ADD KEY idx_reader_content_audit_source_task (source_task_id, source_task_book_id);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
SELECT 1761400000000000015, '书源采集中心', 1761400000000000010, 5, 'source-center', 'reader-admin/source-center/index', '', 'N', 'Y', 'C', '0', '0', 'reader:source:list', 'connection', 1761000000000000103, 1761100000000000001, NOW(), '书源采集中心菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000015);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
SELECT 1761400000000000016, '作品分类', 1761400000000000010, 6, 'work-category', 'reader-admin/work-category/index', '', 'N', 'Y', 'C', '0', '0', 'reader:category:list', 'collection-tag', 1761000000000000103, 1761100000000000001, NOW(), '作品分类菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000016);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, 1761400000000000015
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1761300000000000003 AND menu_id = 1761400000000000015);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, 1761400000000000016
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1761300000000000003 AND menu_id = 1761400000000000016);
