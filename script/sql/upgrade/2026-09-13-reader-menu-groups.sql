-- 阅读器管理菜单分组升级，幂等执行，不修改阅读器业务数据。
-- 作品管理：作品、导入、审核、发布、分类和榜单。
-- 采集中心：采集任务和采集数据大盘。
-- H5 管理：面向移动端运营的用户、书评、积分和反馈入口。

START TRANSACTION;

UPDATE sys_menu SET icon = 'documentation', remark = '阅读器管理总目录'
WHERE menu_id = 1761400000000000010;

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000020, '作品管理', 1761400000000000010, 1, 'works', 'ParentView', '', 'N', 'Y', 'M', '0', '0', '',
       'documentation', '', '', 1761000000000000103, 1761100000000000001, sysdate(), NULL, NULL, '阅读器作品运营菜单分组'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000020);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000021, '采集中心', 1761400000000000010, 2, 'sources', 'ParentView', '', 'N', 'Y', 'M', '0', '0', 'reader:source:list',
       'link', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, '阅读器书源采集菜单分组'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000021);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000022, 'H5 管理', 1761400000000000010, 3, 'h5', 'ParentView', '', 'N', 'Y', 'M', '0', '0', 'reader:h5-user:list,reader:h5-comment:list,reader:h5-points:list,reader:feedback:list',
       'phone', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, '阅读器 H5 运营菜单分组'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000022);

UPDATE sys_menu SET perms = 'reader:h5-user:list,reader:h5-comment:list,reader:h5-points:list,reader:feedback:list', icon = 'phone', remark = '阅读器 H5 运营菜单分组'
WHERE menu_id = 1761400000000000022;

UPDATE sys_menu SET menu_name = '作品列表', parent_id = 1761400000000000020, order_num = 1, path = 'list', component = 'reader-admin/work/index', icon = 'clipboard',
  remark = '作品列表菜单'
WHERE menu_id = 1761400000000000011;
UPDATE sys_menu SET parent_id = 1761400000000000020, order_num = 2, path = 'import-task', component = 'reader-admin/import-task/index', icon = 'upload',
  remark = '导入任务菜单'
WHERE menu_id = 1761400000000000012;
UPDATE sys_menu SET parent_id = 1761400000000000020, order_num = 3, path = 'audit', component = 'reader-admin/audit/index', icon = 'finish',
  remark = '内容审核菜单'
WHERE menu_id = 1761400000000000013;
UPDATE sys_menu SET parent_id = 1761400000000000020, order_num = 4, path = 'publish-log', component = 'reader-admin/publish-log/index', icon = 'time',
  remark = '发布记录菜单'
WHERE menu_id = 1761400000000000014;
UPDATE sys_menu SET parent_id = 1761400000000000020, order_num = 5, path = 'category', component = 'reader-admin/work-category/index', icon = 'category',
  remark = '作品分类菜单'
WHERE menu_id = 1761400000000000016;
UPDATE sys_menu SET parent_id = 1761400000000000020, order_num = 6, path = 'ranking', component = 'reader-admin/ranking/index', icon = 'star',
  remark = '榜单管理菜单'
WHERE menu_id = 1761400000000000017;
UPDATE sys_menu SET menu_name = '采集任务', parent_id = 1761400000000000021, order_num = 1, path = 'tasks', component = 'reader-admin/source-center/index', icon = 'link',
  remark = '书源采集任务菜单'
WHERE menu_id = 1761400000000000015;
UPDATE sys_menu SET parent_id = 1761400000000000021, order_num = 2, path = 'dashboard', component = 'reader-admin/source-dashboard/index', icon = 'chart',
  remark = '采集数据大盘菜单'
WHERE menu_id = 1761400000000000018;

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000019, '用户反馈', 1761400000000000022, 1, 'feedback', 'reader-admin/feedback/index', '', 'N', 'Y', 'C', '0', '0',
       'reader:feedback:list', 'message', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, 'H5 用户反馈菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000019);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000023, '读者用户', 1761400000000000022, 1, 'users', 'reader-admin/h5-user/index', '', 'N', 'Y', 'C', '0', '0',
       'reader:h5-user:list', 'user', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, 'H5 读者用户菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000023);
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000024, '书评管理', 1761400000000000022, 2, 'comments', 'reader-admin/h5-comment/index', '', 'N', 'Y', 'C', '0', '0',
       'reader:h5-comment:list', 'message', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, 'H5 书评管理菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000024);
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000025, '积分规则', 1761400000000000022, 3, 'points', 'reader-admin/h5-points/index', '', 'N', 'Y', 'C', '0', '0',
       'reader:h5-points:list', 'money', '', '', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, 'H5 积分规则菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000025);
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 1761400000000000019;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, menu_id
FROM sys_menu
WHERE menu_id IN (1761400000000000020, 1761400000000000021, 1761400000000000022, 1761400000000000019, 1761400000000000023, 1761400000000000024, 1761400000000000025)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm
    WHERE rm.role_id = 1761300000000000003 AND rm.menu_id = sys_menu.menu_id
  );

COMMIT;
