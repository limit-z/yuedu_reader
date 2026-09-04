-- 阅读器账号绑定能力升级脚本
-- 日期：2026-08-31
-- 作用：
-- 1. 新增读者账号绑定表，承接手机号、邮箱、微信等多端登录绑定

CREATE TABLE IF NOT EXISTS reader_account_bind (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '绑定记录ID',
  account_id BIGINT NOT NULL COMMENT '绑定到的读者账号ID',
  bind_type VARCHAR(32) NOT NULL COMMENT '绑定类型：PHONE、EMAIL、WECHAT_OPENID、WECHAT_UNIONID',
  bind_key VARCHAR(255) NOT NULL COMMENT '绑定值，手机号、邮箱或微信标识',
  bind_source VARCHAR(32) NOT NULL DEFAULT 'LOGIN' COMMENT '绑定来源：LOGIN、PROFILE、BIND、MIGRATE',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_account_bind_type_key (bind_type, bind_key),
  UNIQUE KEY uk_reader_account_bind_account_type (account_id, bind_type),
  KEY idx_reader_account_bind_account (account_id, create_time)
) COMMENT='阅读器账号绑定表';
