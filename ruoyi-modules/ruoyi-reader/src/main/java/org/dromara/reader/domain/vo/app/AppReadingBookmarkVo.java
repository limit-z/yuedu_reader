package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读书签视图对象。
 */
@Data
public class AppReadingBookmarkVo {

    /**
     * 书签ID。
     */
    private Long bookmarkId;

    /**
     * 作品ID。
     */
    private Long workId;

    /**
     * 作品标题。
     */
    private String workTitle;

    /**
     * 作品类型。
     */
    private String workType;

    /**
     * 章节ID。
     */
    private Long chapterId;

    /**
     * 章节名称。
     */
    private String chapterName;

    /**
     * 章节序号。
     */
    private Integer chapterNo;

    /**
     * 阅读定位值。
     */
    private String locationValue;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
