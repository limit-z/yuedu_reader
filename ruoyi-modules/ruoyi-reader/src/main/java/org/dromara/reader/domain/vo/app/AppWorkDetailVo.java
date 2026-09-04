package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 AppWorkDetailVo 相关业务能力。
 */
@Data
public class AppWorkDetailVo {

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
     * 简介内容。
     */
    private String intro;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 内容分类，如玄幻、言情、修仙；与作品类型分开。
     */
    private String categoryName;
    /**
     * 连载状态。
     */
    private String serialStatus;
    /**
     * 发布状态。
     */
    private String publishStatus;
    /**
     * 总章节数。
     */
    private Integer totalChapters;
    /**
     * 总页数。
     */
    private Integer totalPages;
    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 最新章节ID。
     */
    private Long latestChapterId;
    /**
     * 最新章节名。
     */
    private String latestChapterName;
    /**
     * 是否已加入书架。
     */
    private Boolean isOnBookshelf;
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
