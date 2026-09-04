-- 阅读器书签能力升级脚本
-- 日期：2026-08-29
-- 作用：
-- 1. 新增阅读书签表，承接 H5 目录页“书签”标签能力

CREATE TABLE IF NOT EXISTS reader_reading_bookmark (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '书签ID',
  user_id BIGINT NOT NULL COMMENT '读者主体ID，登录态为 sys_user.user_id，游客态为 reader_visitor_account.id',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  work_title VARCHAR(255) NULL COMMENT '作品标题快照，避免作品标题后续变更影响历史展示',
  work_type VARCHAR(16) NULL COMMENT '作品类型：NOVEL小说、COMIC漫画',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  chapter_name VARCHAR(255) NULL COMMENT '章节名称快照',
  chapter_no INT NULL COMMENT '章节序号快照',
  location_value VARCHAR(128) NULL COMMENT '阅读定位值，小说可存段落偏移、漫画可存页码',
  create_dept BIGINT(20) NULL COMMENT '创建部门',
  create_by BIGINT(20) NULL COMMENT '创建人',
  create_time DATETIME NULL COMMENT '创建时间',
  update_by BIGINT(20) NULL COMMENT '更新人',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_reader_bookmark_user_work_chapter (user_id, work_id, chapter_id),
  KEY idx_reader_bookmark_user (user_id, create_time),
  KEY idx_reader_bookmark_work (work_id, create_time)
) COMMENT='阅读器书签表';
