-- 阅读器作品横竖双封面扩展
-- 竖版继续使用 cover_url，新增横版字段供首页推荐等宽图场景使用。

ALTER TABLE reader_work
  ADD COLUMN cover_landscape_url VARCHAR(500) NULL COMMENT '作品横版封面地址，用于宽图推荐位' AFTER cover_url;
