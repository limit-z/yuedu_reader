-- 阅读器首页运营、消息中心与点评基础能力升级脚本
-- 日期：2026-08-15
-- 作用：
-- 1. 新增读者消息表，承接消息中心与反馈回复通知
-- 2. 新增首页公告、横幅、专题及专题作品关联表，承接 H5 v1.0 运营位配置
-- 3. 新增阅读点评表，承接正文选区点评与评论列表能力

CREATE TABLE IF NOT EXISTS reader_user_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '站内消息ID',
  reader_id BIGINT NOT NULL COMMENT '读者主体ID，登录态为 sys_user.user_id，游客态为 reader_visitor_account.id',
  account_type VARCHAR(16) NOT NULL COMMENT '读者身份类型：USER登录用户、VISITOR游客',
  message_type VARCHAR(32) NOT NULL COMMENT '消息类型：NOTICE公告、UPDATE更新、FEEDBACK反馈回复、SYSTEM系统消息',
  title VARCHAR(255) NOT NULL COMMENT '消息标题',
  content VARCHAR(2000) NOT NULL COMMENT '消息正文',
  biz_key VARCHAR(64) NULL COMMENT '业务关联键，如反馈ID、专题Key、作品ID等',
  link_type VARCHAR(32) NULL COMMENT '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链、FEEDBACK反馈单',
  link_value VARCHAR(255) NULL COMMENT '跳转值，如作品ID、专题Key、URL等',
  read_status CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否已读：0未读、1已读',
  read_time DATETIME NULL COMMENT '首次已读时间',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_message_reader (reader_id, account_type, read_status, create_time),
  KEY idx_reader_message_type (message_type, create_time)
) COMMENT='阅读器用户站内消息表';

CREATE TABLE IF NOT EXISTS reader_home_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '首页公告ID',
  notice_title VARCHAR(255) NOT NULL COMMENT '公告标题，用于滚动公告主文案展示',
  notice_content VARCHAR(1000) NULL COMMENT '公告详情，用于公告详情页或气泡补充说明',
  target_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链',
  target_value VARCHAR(255) NULL COMMENT '跳转值，如作品ID、专题Key、URL等',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  start_time DATETIME NULL COMMENT '生效开始时间，为空表示立即生效',
  end_time DATETIME NULL COMMENT '生效结束时间，为空表示长期生效',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_notice_status_sort (status, sort_no, create_time)
) COMMENT='阅读器首页滚动公告表';

CREATE TABLE IF NOT EXISTS reader_home_banner (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '首页横幅ID',
  banner_title VARCHAR(255) NOT NULL COMMENT '横幅标题',
  banner_subtitle VARCHAR(500) NULL COMMENT '横幅副标题，用于补充推荐语',
  image_url VARCHAR(500) NULL COMMENT '横幅图片地址',
  background_color VARCHAR(32) NULL COMMENT '横幅背景色，便于客户端在无图时回退展示',
  target_type VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链',
  target_value VARCHAR(255) NULL COMMENT '跳转值，如作品ID、专题Key、URL等',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  start_time DATETIME NULL COMMENT '生效开始时间，为空表示立即生效',
  end_time DATETIME NULL COMMENT '生效结束时间，为空表示长期生效',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_banner_status_sort (status, sort_no, create_time)
) COMMENT='阅读器首页横幅配置表';

CREATE TABLE IF NOT EXISTS reader_topic (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '专题ID',
  topic_key VARCHAR(64) NOT NULL COMMENT '专题唯一标识，用于前后端路由与外部引用',
  topic_name VARCHAR(255) NOT NULL COMMENT '专题名称',
  topic_desc VARCHAR(1000) NULL COMMENT '专题描述',
  cover_url VARCHAR(500) NULL COMMENT '专题封面地址',
  badge_text VARCHAR(64) NULL COMMENT '专题角标文案，如热映原著、经典必读',
  work_type VARCHAR(16) NULL COMMENT '作品类型：NOVEL小说、COMIC漫画，为空表示混合专题',
  more_path VARCHAR(255) NULL COMMENT '客户端查看更多路径，便于书城直接跳转专题页',
  show_home CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否在书城首页展示：1展示、0不展示',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status CHAR(1) NOT NULL DEFAULT '1' COMMENT '状态：1启用、0停用',
  start_time DATETIME NULL COMMENT '生效开始时间，为空表示立即生效',
  end_time DATETIME NULL COMMENT '生效结束时间，为空表示长期生效',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_topic_key (topic_key),
  KEY idx_reader_topic_status_sort (status, show_home, sort_no, create_time)
) COMMENT='阅读器专题主表';

CREATE TABLE IF NOT EXISTS reader_topic_work (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '专题作品关联ID',
  topic_id BIGINT NOT NULL COMMENT '所属专题ID',
  work_id BIGINT NOT NULL COMMENT '所属作品ID',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '专题内排序值，数值越小越靠前',
  remark VARCHAR(255) NULL COMMENT '专题推荐语，如影视原著、口碑神作',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_topic_work (topic_id, work_id),
  KEY idx_reader_topic_work_sort (topic_id, sort_no, id)
) COMMENT='阅读器专题作品关联表';

CREATE TABLE IF NOT EXISTS reader_reading_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '阅读点评ID',
  reader_id BIGINT NOT NULL COMMENT '读者主体ID，登录态为 sys_user.user_id，游客态为 reader_visitor_account.id',
  account_type VARCHAR(16) NOT NULL COMMENT '读者身份类型：USER登录用户、VISITOR游客',
  work_id BIGINT NOT NULL COMMENT '所属作品ID',
  chapter_id BIGINT NOT NULL COMMENT '所属章节ID',
  quote_text VARCHAR(1000) NULL COMMENT '引用原文片段',
  quote_start INT NULL COMMENT '引用起始偏移，基于章节正文字符位置',
  quote_end INT NULL COMMENT '引用结束偏移，基于章节正文字符位置',
  comment_content VARCHAR(2000) NOT NULL COMMENT '点评正文内容',
  score INT NULL COMMENT '点评评分，预留 1-5 星评价能力',
  like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  status VARCHAR(32) NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE可见、HIDDEN隐藏、DELETED删除',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  KEY idx_reader_comment_work_chapter (work_id, chapter_id, status, create_time),
  KEY idx_reader_comment_reader (reader_id, account_type, create_time)
) COMMENT='阅读器正文点评表';
