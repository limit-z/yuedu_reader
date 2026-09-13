package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读器模块代码，承载 AppWorkCardVo 相关业务能力。
 */
@Data
public class AppWorkCardVo {

    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 作者。
     */
    private String authorName;
    /**
     * 封面地址。
     */
    private String coverUrl;
    /**
     * 横版封面地址。
     */
    private String coverLandscapeUrl;
    /**
     * 封面背景模式：GLOBAL、COLOR、IMAGE。
     */
    private String coverBackgroundMode;
    /**
     * 作品级封面背景色。
     */
    private String coverBackgroundColor;
    /**
     * 作品级封面背景图片地址。
     */
    private String coverBackgroundImageUrl;
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
     * 连载状态：ONGOING、FINISHED。
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
