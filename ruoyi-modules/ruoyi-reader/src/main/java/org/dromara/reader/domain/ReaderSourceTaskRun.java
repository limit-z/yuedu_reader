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
    private Integer requestCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failureCount;
    private Integer tooManyRequestsCount;
    private String circuitOpen;
    private String errorMessage;
    private String resultSummary;
}
