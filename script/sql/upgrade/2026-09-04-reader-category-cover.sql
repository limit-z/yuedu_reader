-- 阅读器作品分类与封面字段升级
-- 适用于已执行过旧版 reader schema 的数据库；新库请使用 script/sql/ry_reader.sql。

ALTER TABLE reader_work
    ADD COLUMN category_name varchar(64) NULL COMMENT '内容分类：玄幻、言情、修仙等，与作品类型分开';

ALTER TABLE reader_import_task
    ADD COLUMN category_name varchar(64) NULL COMMENT '作品内容分类：玄幻、言情、修仙等';

ALTER TABLE reader_import_task
    ADD COLUMN cover_oss_id bigint NULL COMMENT '作品封面OSS文件ID，可为空';
