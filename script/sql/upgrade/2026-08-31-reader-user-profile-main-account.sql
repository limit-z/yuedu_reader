-- 阅读器账号主表升级脚本
-- 日期：2026-08-31
-- 作用：
-- 1. 新增读者账号主表，承接账号状态与登录元数据
-- 2. 兼容性地为现有 reader_user_profile 记录补齐对应账号数据

CREATE TABLE IF NOT EXISTS reader_account (
  account_id BIGINT PRIMARY KEY COMMENT '读者账号ID',
  status CHAR(1) NOT NULL DEFAULT '0' COMMENT '账号状态：0正常、1停用',
  last_login_at DATETIME NULL COMMENT '最近登录时间',
  last_client_type VARCHAR(32) NULL COMMENT '最近登录客户端类型',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间'
) COMMENT='阅读器读者账号主表';

INSERT IGNORE INTO reader_account (account_id, status, create_dept, create_by, create_time, update_by, update_time)
SELECT account_id, '0', create_dept, create_by, create_time, update_by, update_time
FROM reader_user_profile;
