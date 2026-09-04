package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读点评视图对象。
 */
@Data
public class AppReadingCommentVo {

    /**
     * 点评ID。
     */
    private Long commentId;

    /**
     * 作品ID。
     */
    private Long workId;

    /**
     * 章节ID。
     */
    private Long chapterId;

    /**
     * 作者昵称。
     */
    private String nickName;

    /**
     * 作者头像样式。
     */
    private String avatarStyle;

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
     * 创建时间。
     */
    private LocalDateTime createTime;
}
