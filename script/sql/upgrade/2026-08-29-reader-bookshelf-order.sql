ALTER TABLE reader_bookshelf
    ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前' AFTER top_pin;

CREATE INDEX idx_reader_bookshelf_user_order
    ON reader_bookshelf (user_id, top_pin, sort_no, update_time);
