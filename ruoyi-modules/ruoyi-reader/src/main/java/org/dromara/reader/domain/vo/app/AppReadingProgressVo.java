package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 AppReadingProgressVo 相关业务能力。
 */
@Data
public class AppReadingProgressVo {

    /**
     * 作品ID。
     */
    private Long workId;
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
     * 阅读定位原始值。H5 使用 JSON 保存页码、正文偏移和滚动位置。
     */
    private String locationValue;
    /**
     * 已读百分比。
     */
    private Integer readPercent;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 客户端类型。
     */
    private String clientType;
    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
