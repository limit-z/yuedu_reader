package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 采集任务运行记录，保存脱敏统计和心跳。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_task_run")
public class ReaderSourceTaskRun extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long taskId;
    private String runToken;
    private String executorType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime heartbeatAt;
    /** 首次被 Worker 成功领取的时间；为空表示仍在等待领取。 */
    private LocalDateTime claimedAt;
    private Integer requestCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failureCount;
    private Integer tooManyRequestsCount;
    private String circuitOpen;
    private String errorMessage;
    private String resultSummary;
    /** 本任务的每日额度自动重试序号，手工运行记为0。 */
    private Integer retryNo;
    /** 运行触发类型：MANUAL、DAILY_LIMIT。 */
    private String triggerType;
    /** 运行触发原因。 */
    private String triggerReason;
}
