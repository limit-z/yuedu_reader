-- 阅读器用户设置升级脚本
-- 日期：2026-08-31
-- 作用：
-- 1. 新增读者用户设置表，承接阅读主题、字号、翻页方式等偏好配置
-- 2. 兼容性迁移现有 reader_user_profile 中的偏好数据
-- 3. 兼容性迁移现有 reader_user_profile 中的微信联系方式到绑定表

CREATE TABLE IF NOT EXISTS reader_user_setting (
  account_id BIGINT PRIMARY KEY COMMENT '读者账号ID',
  preferences_json TEXT NULL COMMENT '阅读偏好 JSON，如界面主题、阅读主题、字号、翻页方式等',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间'
) COMMENT='阅读器用户设置表';

INSERT IGNORE INTO reader_user_setting (account_id, preferences_json, create_dept, create_by, create_time, update_by, update_time)
SELECT account_id, preferences_json, create_dept, create_by, create_time, update_by, update_time
FROM reader_user_profile
WHERE preferences_json IS NOT NULL AND preferences_json <> '';

INSERT IGNORE INTO reader_account_bind (account_id, bind_type, bind_key, bind_source, create_dept, create_by, create_time, update_by, update_time)
SELECT account_id, 'WECHAT_NO', wechat_no, 'MIGRATE', create_dept, create_by, create_time, update_by, update_time
FROM reader_user_profile
WHERE wechat_no IS NOT NULL AND wechat_no <> '';
