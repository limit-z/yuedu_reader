package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 采集中心按小说聚合后的追踪行。 */
@Data
public class ReaderSourceDashboardWorkVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long workId;
    private String title;
    private String authorName;
    private String categoryName;
    private String serialStatus;
    private String publishStatus;
    private Long taskCount;
    private Long bookRecordCount;
    private Long plannedChapterCount;
    private Long processedChapterCount;
    private Long successChapterCount;
    private Long skippedChapterCount;
    private Long failedChapterCount;
    private Long chapterTotal;
    private Long contentReadyChapterCount;
    private Long contentMissingChapterCount;
    private Long currentTaskId;
    private String currentTaskName;
    private String currentTaskStatus;
    private Integer progressPercent;
    private LocalDateTime lastActivityAt;
}
