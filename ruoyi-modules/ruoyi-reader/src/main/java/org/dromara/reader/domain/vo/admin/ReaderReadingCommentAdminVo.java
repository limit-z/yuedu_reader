package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** H5 书评管理视图。 */
@Data
public class ReaderReadingCommentAdminVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long readerId;
    private String accountType;
    private Long workId;
    private String workTitle;
    private Long chapterId;
    private String quoteText;
    private String commentContent;
    private Integer score;
    private Integer likeCount;
    private String status;
    private String nickName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
