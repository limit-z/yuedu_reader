package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小说章节正文实体，负责把大体量正文与章节元数据分表存储。
 */
@Data
@TableName("reader_novel_chapter_content")
public class ReaderNovelChapterContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 章节ID，同时也是正文记录主键。
     */
    @TableId(value = "chapter_id")
    private Long chapterId;

    /**
     * 章节正文内容。
     */
    private String content;
}
