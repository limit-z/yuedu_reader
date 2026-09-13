package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 小说下的单个采集任务进度。 */
@Data
public class ReaderSourceDashboardTaskVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String taskName;
    private Long siteId;
    private String siteName;
    private String executorType;
    private String status;
    private String failureCode;
    private String failReason;
    private Long bookCount;
    private Long plannedChapterCount;
    private Long processedChapterCount;
    private Long successChapterCount;
    private Long skippedChapterCount;
    private Long failedChapterCount;
    private Integer progressPercent;
    private Boolean current;
    private LocalDateTime lastRunAt;
    private LocalDateTime lastActivityAt;
}
