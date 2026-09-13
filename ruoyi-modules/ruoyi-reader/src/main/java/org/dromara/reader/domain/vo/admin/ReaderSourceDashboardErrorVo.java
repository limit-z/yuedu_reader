package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReaderSourceDashboardErrorVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private Long runId;
    private String errorType;
    private Integer httpStatus;
    private String message;
    private String resolved;
    private LocalDateTime retryAt;
    private LocalDateTime createTime;
}
