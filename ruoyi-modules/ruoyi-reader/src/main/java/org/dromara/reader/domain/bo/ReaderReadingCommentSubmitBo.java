package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读点评提交业务对象。
 */
@Data
public class ReaderReadingCommentSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
}
