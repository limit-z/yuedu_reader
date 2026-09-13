-- 清理重复的书源采集中心菜单。
-- 规范菜单 ID 为 1761400000000000015；不触碰采集任务、运行记录及业务数据。

START TRANSACTION;

DELETE FROM sys_role_menu
WHERE menu_id = 1761400000000015000;

DELETE FROM sys_menu
WHERE menu_id = 1761400000000015000
  AND parent_id = 1761400000000000010
  AND path = 'source-center'
  AND component = 'reader-admin/source-center/index';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1761300000000000003, 1761400000000000015
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1761400000000000015)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu
    WHERE role_id = 1761300000000000003 AND menu_id = 1761400000000000015
  );

COMMIT;
