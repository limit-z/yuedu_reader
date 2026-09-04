-- 阅读器用户主表简化脚本
-- 日期：2026-08-31
-- 作用：
-- 1. 保留一张读者主表 reader_user_profile，承载账号状态、基础资料和阅读偏好
-- 2. 保留必要的绑定表 reader_account_bind，承载手机号/邮箱/微信等多绑定关系
-- 3. 删除冗余的 reader_account 和 reader_user_setting

ALTER TABLE reader_user_profile
  ADD COLUMN IF NOT EXISTS status CHAR(1) NOT NULL DEFAULT '0' COMMENT '账号状态：0正常、1停用' AFTER account_id,
  ADD COLUMN IF NOT EXISTS last_login_at DATETIME NULL COMMENT '最近登录时间' AFTER status,
  ADD COLUMN IF NOT EXISTS last_client_type VARCHAR(32) NULL COMMENT '最近登录客户端类型' AFTER last_login_at,
  ADD COLUMN IF NOT EXISTS nick_name VARCHAR(64) NULL COMMENT '读者昵称' AFTER last_client_type,
  ADD COLUMN IF NOT EXISTS gender VARCHAR(16) NULL COMMENT '读者性别：MALE男、FEMALE女、UNKNOWN未知' AFTER nick_name,
  ADD COLUMN IF NOT EXISTS birthday VARCHAR(20) NULL COMMENT '读者生日，建议使用 yyyy-MM-dd 格式' AFTER gender,
  ADD COLUMN IF NOT EXISTS region VARCHAR(64) NULL COMMENT '所在地区' AFTER birthday,
  ADD COLUMN IF NOT EXISTS signature VARCHAR(255) NULL COMMENT '个性签名' AFTER region,
  ADD COLUMN IF NOT EXISTS preferences_json TEXT NULL COMMENT '阅读偏好 JSON，如界面主题、阅读主题、字号、翻页方式等' AFTER wechat_no;

ALTER TABLE reader_user_profile COMMENT='阅读器用户主表';

DROP TABLE IF EXISTS reader_account;
DROP TABLE IF EXISTS reader_user_setting;
