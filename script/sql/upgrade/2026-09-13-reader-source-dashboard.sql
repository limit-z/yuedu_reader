-- 采集中心大盘菜单，幂等执行，不修改任何采集数据。
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type,
   visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1761400000000000018, '采集数据大盘', 1761400000000000010, 4, 'source-dashboard',
       'reader-admin/source-dashboard/index', '', 'N', 'Y', 'C', '0', '0', 'reader:source:list',
       'data-analysis', 1761000000000000010, 1761100000000000001, sysdate(), NULL, NULL, '采集中心全链路统计大盘'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE parent_id = 1761400000000000010 AND path = 'source-dashboard'
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, 1761400000000000018
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu
  WHERE role_id = 1761300000000000003 AND menu_id = 1761400000000000018
);
