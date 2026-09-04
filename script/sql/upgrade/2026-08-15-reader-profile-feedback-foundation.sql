-- 阅读器资料与反馈基础能力升级脚本
-- 日期：2026-08-15
-- 作用：
-- 1. 扩展游客账户表，支持游客态资料与偏好持久化
-- 2. 新增阅读器用户扩展资料表，承接昵称、头像、性别、签名与偏好设置
-- 3. 新增用户反馈工单表，承接小程序“意见反馈”与后台处理闭环

ALTER TABLE reader_visitor_account
  ADD COLUMN nick_name varchar(64) null comment '游客昵称，用于“我的”页资料卡与反馈提单回显' AFTER visitor_id;

ALTER TABLE reader_visitor_account
  ADD COLUMN avatar_style varchar(64) null comment '游客头像样式标识，先用于本地头像主题与演示头像风格回显' AFTER nick_name;

ALTER TABLE reader_visitor_account
  ADD COLUMN gender varchar(16) null comment '游客性别：MALE男、FEMALE女、UNKNOWN未知' AFTER avatar_style;

ALTER TABLE reader_visitor_account
  ADD COLUMN mobile varchar(32) null comment '游客手机号展示字段，后续绑定手机号后可回写' AFTER gender;

ALTER TABLE reader_visitor_account
  ADD COLUMN wechat_no varchar(64) null comment '游客微信号展示字段，后续绑定微信后可回写' AFTER mobile;

ALTER TABLE reader_visitor_account
  ADD COLUMN preferences_json text null comment '游客阅读偏好 JSON，如界面主题、阅读主题、翻页方式等' AFTER wechat_no;

CREATE TABLE IF NOT EXISTS reader_user_profile (
  account_id bigint primary key comment '读者账号ID',
  nick_name varchar(64) null comment '读者昵称',
  gender varchar(16) null comment '读者性别：MALE男、FEMALE女、UNKNOWN未知',
  avatar_style varchar(64) null comment '阅读器端头像样式标识，先用于演示头像主题与轻量头像方案',
  birthday varchar(20) null comment '读者生日，建议使用 yyyy-MM-dd 格式',
  region varchar(64) null comment '所在地区',
  signature varchar(255) null comment '个性签名',
  wechat_no varchar(64) null comment '绑定微信号展示值，正式版可扩展为 OpenID/UnionID 关系表',
  preferences_json text null comment '读者偏好 JSON，保存界面主题、阅读主题、字号、翻页方式等客户端设置',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='阅读器用户资料表';

CREATE TABLE IF NOT EXISTS reader_user_feedback (
  id bigint primary key auto_increment comment '反馈记录ID',
  reader_id bigint not null comment '读者主体ID，登录态为 reader_account.account_id，游客态为 reader_visitor_account.id',
  account_type varchar(16) not null comment '读者身份类型：USER登录用户、VISITOR游客',
  feedback_type varchar(32) not null comment '反馈类型：功能异常、内容问题、体验建议、账号问题等',
  feedback_content varchar(2000) not null comment '反馈正文内容',
  contact_mobile varchar(32) null comment '提交时带上的手机号，便于管理员联系读者',
  contact_wechat varchar(64) null comment '提交时带上的微信号，便于管理员联系读者',
  nick_name varchar(64) null comment '提交时使用的昵称快照，避免后续改名影响工单追溯',
  status varchar(32) not null default 'PENDING' comment '处理状态：PENDING待处理、PROCESSING处理中、REPLIED已回复、DONE已完成',
  reply_content varchar(2000) null comment '管理员回复内容',
  reply_by bigint null comment '最后回复的管理员用户ID',
  reply_time datetime null comment '最后回复时间',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_feedback_reader (reader_id, account_type, create_time),
  key idx_reader_feedback_status (status, create_time)
) comment='阅读器用户反馈工单表';
