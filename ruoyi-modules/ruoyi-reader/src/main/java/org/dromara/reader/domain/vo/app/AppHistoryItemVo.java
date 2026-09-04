package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 AppHistoryItemVo 相关业务能力。
 */
@Data
public class AppHistoryItemVo {

    /**
     * 历史记录ID。
     */
    private Long historyId;
    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 章节序号。
     */
    private Integer chapterNo;
    /**
     * 页码。
     */
    private Integer pageNo;
    /**
     * 阅读定位原始值，兼容分页和滚动阅读模式。
     */
    private String locationValue;
    /**
     * 客户端类型。
     */
    private String clientType;
    /**
     * 阅读时间。
     */
    private LocalDateTime readAt;
}
