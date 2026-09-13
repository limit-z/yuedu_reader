package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 采集运行记录管理端视图，刻意不暴露一次性 runToken。 */
@Data
public class ReaderSourceTaskRunAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String executorType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime heartbeatAt;
    private LocalDateTime claimedAt;
    private Integer requestCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failureCount;
    private Integer tooManyRequestsCount;
    private String circuitOpen;
    private String errorMessage;
    private String resultSummary;
    private Integer retryNo;
    private String triggerType;
    private String triggerReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
