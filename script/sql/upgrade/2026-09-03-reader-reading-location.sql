-- 阅读进度 / 阅读历史定位字段升级脚本
-- 日期：2026-09-03
-- 作用：为分页与上下滚动模式保存完整 JSON 阅读定位，兼容旧的纯数字页码值

ALTER TABLE reader_reading_history
  MODIFY COLUMN location_value VARCHAR(512) NULL COMMENT '阅读定位值，支持页码、正文偏移与滚动位置JSON';

ALTER TABLE reader_reading_progress
  MODIFY COLUMN location_value VARCHAR(512) NULL COMMENT '阅读定位值，支持页码、正文偏移与滚动位置JSON';
