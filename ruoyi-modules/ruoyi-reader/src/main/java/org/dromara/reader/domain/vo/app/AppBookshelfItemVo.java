package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 AppBookshelfItemVo 相关业务能力。
 */
@Data
public class AppBookshelfItemVo {

    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 封面地址。
     */
    private String coverUrl;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 最新章节名。
     */
    private String latestChapterName;
    /**
     * 是否置顶。
     */
    private Boolean topPin;
    /**
     * 手动排序值。
     */
    private Integer sortNo;
    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 继续阅读章节ID。
     */
    private Long continueChapterId;
    /**
     * 继续阅读章节名。
     */
    private String continueChapterName;
    /**
     * 继续阅读进度。
     */
    private Integer continueProgress;
}
