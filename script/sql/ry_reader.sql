-- Reader module schema
-- Date: 2026-08-09

create table if not exists reader_work (
  id bigint primary key auto_increment comment '作品ID',
  work_type varchar(16) not null comment '作品类型：NOVEL小说、COMIC漫画',
  category_name varchar(64) null comment '内容分类：玄幻、言情、修仙等，与作品类型分开',
  title varchar(255) not null comment '作品标题',
  author_name varchar(128) not null default '未知作者' comment '作品作者，用于作品去重与展示',
  dedupe_key char(64) not null default '' comment '规范化书名+作者 SHA-256 去重键',
  intro text null comment '作品简介',
  cover_url varchar(500) null comment '作品封面地址',
  cover_landscape_url varchar(500) null comment '作品横版封面地址，用于宽图推荐位',
  cover_background_mode varchar(16) not null default 'GLOBAL' comment '自动封面背景模式：GLOBAL继承全局、COLOR背景色、IMAGE背景图片',
  cover_background_color char(7) null comment '作品级自动封面背景色，格式#RRGGBB',
  cover_background_oss_id bigint null comment '作品级自动封面背景图片OSS ID',
  cover_revision int not null default 0 comment '自动封面缓存版本号',
  serial_status varchar(32) not null default 'ONGOING' comment '连载状态：ONGOING连载中、FINISHED已完结',
  publish_status varchar(32) not null default 'DRAFT' comment '发布状态：DRAFT草稿、PUBLISHED已上架、OFFLINE已下架',
  source_type varchar(32) not null default 'IMPORT' comment '内容来源：IMPORT文件导入、SYNC授权同步',
  total_chapters int not null default 0 comment '总章节数',
  total_pages int not null default 0 comment '总页数，主要用于漫画',
  allow_search char(1) not null default '1' comment '是否允许搜索：1允许、0禁止',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
  ,unique key uk_reader_work_dedupe_key (dedupe_key)
) comment='阅读器作品主表';

create table if not exists reader_work_category (
  id bigint primary key auto_increment comment '作品分类ID',
  category_name varchar(64) not null comment '分类展示名称：玄幻、言情、修仙等',
  normalized_name varchar(64) not null comment '规范化分类名称，用于唯一判断',
  source_type varchar(16) not null default 'MANUAL' comment '分类来源：MANUAL手工、SOURCE书源发现',
  status char(1) not null default '1' comment '状态：1启用、0停用',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_work_category_normalized (normalized_name)
) comment='阅读器作品内容分类表';

create table if not exists reader_novel_chapter (
  id bigint primary key auto_increment comment '小说章节ID',
  work_id bigint not null comment '所属作品ID',
  volume_name varchar(255) null comment '卷名',
  chapter_name varchar(255) not null comment '章节标题',
  chapter_no int not null default 0 comment '章节序号',
  word_count int not null default 0 comment '章节字数',
  publish_status varchar(32) not null default 'DRAFT' comment '发布状态：DRAFT草稿、PUBLISHED已发布、OFFLINE已下架',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_novel_chapter_work_no (work_id, chapter_no, id)
) comment='小说章节表';

create table if not exists reader_novel_chapter_content (
  chapter_id bigint primary key comment '章节ID',
  content longtext null comment '章节正文'
) comment='小说章节正文表';

create table if not exists reader_comic_chapter (
  id bigint primary key auto_increment comment '漫画章节ID',
  work_id bigint not null comment '所属作品ID',
  chapter_name varchar(255) not null comment '章节标题',
  chapter_no int not null default 0 comment '章节序号',
  page_count int not null default 0 comment '章节页数',
  publish_status varchar(32) not null default 'DRAFT' comment '发布状态：DRAFT草稿、PUBLISHED已发布、OFFLINE已下架',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='漫画章节表';

create table if not exists reader_comic_page (
  id bigint primary key auto_increment comment '漫画页ID',
  chapter_id bigint not null comment '所属漫画章节ID',
  page_no int not null default 0 comment '页序号',
  image_url varchar(500) not null comment '图片地址',
  width int null comment '图片宽度',
  height int null comment '图片高度',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='漫画分页表';

create table if not exists reader_import_task (
  id bigint primary key auto_increment comment '导入任务ID',
  task_name varchar(255) not null comment '任务名称',
  content_type varchar(16) not null comment '内容类型：NOVEL小说、COMIC漫画',
  category_name varchar(64) null comment '作品内容分类：玄幻、言情、修仙等',
  status varchar(32) not null comment '任务状态：CREATED已创建、PARSING解析中、PENDING_REVIEW待审核、PARSE_FAILED解析失败',
  oss_id bigint null comment '关联OSS文件ID',
  cover_oss_id bigint null comment '作品封面OSS文件ID，可为空',
  total_units int not null default 0 comment '总处理单元数',
  processed_units int not null default 0 comment '已处理单元数',
  progress_percent int not null default 0 comment '处理进度百分比',
  progress_message varchar(255) null comment '进度说明',
  fail_reason varchar(1000) null comment '失败原因',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='导入任务主表';

create table if not exists reader_import_file (
  id bigint primary key auto_increment comment '导入文件记录ID',
  task_id bigint not null comment '所属导入任务ID',
  origin_name varchar(255) not null comment '原始文件名',
  file_suffix varchar(32) not null comment '文件后缀',
  oss_id bigint null comment '关联OSS文件ID',
  file_size bigint null comment '文件大小，单位字节',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='导入文件记录表';

create table if not exists reader_content_audit (
  id bigint primary key auto_increment comment '审核记录ID',
  work_id bigint not null comment '所属作品ID',
  source_task_id bigint null comment '关联书源采集任务ID',
  source_task_book_id bigint null comment '关联书源采集任务书籍明细ID',
  audit_status varchar(32) not null comment '审核状态：PENDING待审核、APPROVED已通过、REJECTED已驳回',
  audit_comment varchar(1000) null comment '审核意见',
  auditor_id bigint null comment '审核人ID',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='内容审核记录表';

create table if not exists reader_bookshelf (
  id bigint primary key auto_increment comment '书架记录ID',
  user_id bigint not null comment '用户ID',
  work_id bigint not null comment '作品ID',
  top_pin char(1) not null default '0' comment '是否置顶：1置顶、0不置顶',
  sort_no int not null default 0 comment '排序值，数值越小越靠前',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_bookshelf_user_work (user_id, work_id),
  key idx_reader_bookshelf_user_order (user_id, top_pin, sort_no, update_time)
) comment='用户书架表';

create table if not exists reader_reading_history (
  id bigint primary key auto_increment comment '阅读历史ID',
  user_id bigint not null comment '用户ID',
  work_id bigint not null comment '作品ID',
  chapter_id bigint not null comment '章节ID',
  content_type varchar(16) not null comment '内容类型：NOVEL小说、COMIC漫画',
  location_value varchar(512) null comment '阅读定位值，支持页码、正文偏移与滚动位置JSON',
  client_type varchar(32) null comment '客户端类型：H5、MP_WEIXIN、APP等',
  read_at datetime null comment '阅读发生时间',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='阅读历史表';

create table if not exists reader_reading_progress (
  id bigint primary key auto_increment comment '阅读进度ID',
  user_id bigint not null comment '用户ID',
  work_id bigint not null comment '作品ID',
  content_type varchar(16) not null comment '内容类型：NOVEL小说、COMIC漫画',
  chapter_id bigint not null comment '当前章节ID',
  location_value varchar(512) null comment '阅读定位值，支持页码、正文偏移与滚动位置JSON',
  progress_percent int not null default 0 comment '阅读进度百分比',
  client_type varchar(32) not null comment '最近写入进度的客户端类型',
  progress_updated_at datetime not null comment '最近一次进度更新时间',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_progress_user_work (user_id, work_id)
) comment='阅读进度表';

create table if not exists reader_visitor_account (
  id bigint primary key auto_increment comment '访客账户ID，同时作为游客态持久化主体ID',
  visitor_id varchar(64) not null comment '前端生成的访客标识',
  nick_name varchar(64) null comment '游客昵称，用于“我的”页资料卡与反馈提单回显',
  avatar_style varchar(64) null comment '游客头像样式标识，先用于本地头像主题与演示头像风格回显',
  gender varchar(16) null comment '游客性别：MALE男、FEMALE女、UNKNOWN未知',
  mobile varchar(32) null comment '游客手机号展示字段，后续绑定手机号后可回写',
  wechat_no varchar(64) null comment '游客微信号展示字段，后续绑定微信后可回写',
  preferences_json text null comment '游客阅读偏好 JSON，如界面主题、阅读主题、翻页方式等',
  last_client_type varchar(32) null comment '最近一次活跃时上报的客户端类型',
  linked_user_id bigint null comment '最近一次完成合并的读者账号ID',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_visitor_account_visitor_id (visitor_id)
) comment='阅读器访客账户映射表';

create table if not exists reader_account (
  account_id bigint primary key comment '读者账号ID',
  status char(1) not null default '0' comment '账号状态：0正常、1停用',
  last_login_at datetime null comment '最近登录时间',
  last_client_type varchar(32) null comment '最近登录客户端类型',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间'
) comment='阅读器读者账号主表';

create table if not exists reader_user_profile (
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
) comment='阅读器读者资料表';

create table if not exists reader_user_feedback (
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

create table if not exists reader_user_message (
  id bigint primary key auto_increment comment '站内消息ID',
  reader_id bigint not null comment '读者主体ID，登录态为 reader_account.account_id，游客态为 reader_visitor_account.id',
  account_type varchar(16) not null comment '读者身份类型：USER登录用户、VISITOR游客',
  message_type varchar(32) not null comment '消息类型：NOTICE公告、UPDATE更新、FEEDBACK反馈回复、SYSTEM系统消息',
  title varchar(255) not null comment '消息标题',
  content varchar(2000) not null comment '消息正文',
  biz_key varchar(64) null comment '业务关联键，如反馈ID、专题Key、作品ID等',
  link_type varchar(32) null comment '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链、FEEDBACK反馈单',
  link_value varchar(255) null comment '跳转值，如作品ID、专题Key、URL等',
  read_status char(1) not null default '0' comment '是否已读：0未读、1已读',
  read_time datetime null comment '首次已读时间',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_message_reader (reader_id, account_type, read_status, create_time),
  key idx_reader_message_type (message_type, create_time)
) comment='阅读器用户站内消息表';

create table if not exists reader_home_notice (
  id bigint primary key auto_increment comment '首页公告ID',
  notice_title varchar(255) not null comment '公告标题，用于滚动公告主文案展示',
  notice_content varchar(1000) null comment '公告详情，用于公告详情页或气泡补充说明',
  target_type varchar(32) not null default 'NONE' comment '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链',
  target_value varchar(255) null comment '跳转值，如作品ID、专题Key、URL等',
  sort_no int not null default 0 comment '排序值，数值越小越靠前',
  status char(1) not null default '1' comment '状态：1启用、0停用',
  start_time datetime null comment '生效开始时间，为空表示立即生效',
  end_time datetime null comment '生效结束时间，为空表示长期生效',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_notice_status_sort (status, sort_no, create_time)
) comment='阅读器首页滚动公告表';

create table if not exists reader_home_banner (
  id bigint primary key auto_increment comment '首页横幅ID',
  banner_title varchar(255) not null comment '横幅标题',
  banner_subtitle varchar(500) null comment '横幅副标题，用于补充推荐语',
  image_url varchar(500) null comment '横幅图片地址',
  background_color varchar(32) null comment '横幅背景色，便于客户端在无图时回退展示',
  target_type varchar(32) not null default 'NONE' comment '跳转类型：NONE无跳转、WORK作品、TOPIC专题、URL外链',
  target_value varchar(255) null comment '跳转值，如作品ID、专题Key、URL等',
  sort_no int not null default 0 comment '排序值，数值越小越靠前',
  status char(1) not null default '1' comment '状态：1启用、0停用',
  start_time datetime null comment '生效开始时间，为空表示立即生效',
  end_time datetime null comment '生效结束时间，为空表示长期生效',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_banner_status_sort (status, sort_no, create_time)
) comment='阅读器首页横幅配置表';

create table if not exists reader_topic (
  id bigint primary key auto_increment comment '专题ID',
  topic_key varchar(64) not null comment '专题唯一标识，用于前后端路由与外部引用',
  topic_name varchar(255) not null comment '专题名称',
  topic_desc varchar(1000) null comment '专题描述',
  cover_url varchar(500) null comment '专题封面地址',
  badge_text varchar(64) null comment '专题角标文案，如热映原著、经典必读',
  work_type varchar(16) null comment '作品类型：NOVEL小说、COMIC漫画，为空表示混合专题',
  more_path varchar(255) null comment '客户端查看更多路径，便于书城直接跳转专题页',
  show_home char(1) not null default '1' comment '是否在书城首页展示：1展示、0不展示',
  sort_no int not null default 0 comment '排序值，数值越小越靠前',
  status char(1) not null default '1' comment '状态：1启用、0停用',
  start_time datetime null comment '生效开始时间，为空表示立即生效',
  end_time datetime null comment '生效结束时间，为空表示长期生效',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_topic_key (topic_key),
  key idx_reader_topic_status_sort (status, show_home, sort_no, create_time)
) comment='阅读器专题主表';

create table if not exists reader_topic_work (
  id bigint primary key auto_increment comment '专题作品关联ID',
  topic_id bigint not null comment '所属专题ID',
  work_id bigint not null comment '所属作品ID',
  sort_no int not null default 0 comment '专题内排序值，数值越小越靠前',
  remark varchar(255) null comment '专题推荐语，如影视原著、口碑神作',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_topic_work (topic_id, work_id),
  key idx_reader_topic_work_sort (topic_id, sort_no, id)
) comment='阅读器专题作品关联表';

create table if not exists reader_reading_comment (
  id bigint primary key auto_increment comment '阅读点评ID',
  reader_id bigint not null comment '读者主体ID，登录态为 reader_account.account_id，游客态为 reader_visitor_account.id',
  account_type varchar(16) not null comment '读者身份类型：USER登录用户、VISITOR游客',
  work_id bigint not null comment '所属作品ID',
  chapter_id bigint not null comment '所属章节ID',
  quote_text varchar(1000) null comment '引用原文片段',
  quote_start int null comment '引用起始偏移，基于章节正文字符位置',
  quote_end int null comment '引用结束偏移，基于章节正文字符位置',
  comment_content varchar(2000) not null comment '点评正文内容',
  score int null comment '点评评分，预留 1-5 星评价能力',
  like_count int not null default 0 comment '点赞数',
  status varchar(32) not null default 'VISIBLE' comment '状态：VISIBLE可见、HIDDEN隐藏、DELETED删除',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  key idx_reader_comment_work_chapter (work_id, chapter_id, status, create_time),
  key idx_reader_comment_reader (reader_id, account_type, create_time)
) comment='阅读器正文点评表';

create table if not exists reader_account_bind (
  id bigint primary key auto_increment comment '绑定记录ID',
  account_id bigint not null comment '绑定到的读者账号ID',
  bind_type varchar(32) not null comment '绑定类型：PHONE、EMAIL、WECHAT_OPENID、WECHAT_UNIONID',
  bind_key varchar(255) not null comment '绑定值，手机号、邮箱或微信标识',
  bind_source varchar(32) not null default 'LOGIN' comment '绑定来源：LOGIN、PROFILE、BIND、MIGRATE',
  create_dept bigint(20) null comment '创建部门',
  create_by bigint(20) null comment '创建人',
  create_time datetime null comment '创建时间',
  update_by bigint(20) null comment '更新人',
  update_time datetime null comment '更新时间',
  unique key uk_reader_account_bind_type_key (bind_type, bind_key),
  unique key uk_reader_account_bind_account_type (account_id, bind_type),
  key idx_reader_account_bind_account (account_id, create_time)
) comment='阅读器账号绑定表';
