package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器正文点评实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_reading_comment")
public class ReaderReadingComment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 点评ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 读者主体ID。
     */
    private Long readerId;

    /**
     * 读者身份类型。
     */
    private String accountType;

    /**
     * 作品ID。
     */
    private Long workId;

    /**
     * 章节ID。
     */
    private Long chapterId;

    /**
     * 引用原文。
     */
    private String quoteText;

    /**
     * 引用起始位置。
     */
    private Integer quoteStart;

    /**
     * 引用结束位置。
     */
    private Integer quoteEnd;

    /**
     * 点评正文。
     */
    private String commentContent;

    /**
     * 点评分数。
     */
    private Integer score;

    /**
     * 点赞数。
     */
    private Integer likeCount;

    /**
     * 状态。
     */
    private String status;
}
