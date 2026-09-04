package org.dromara.reader.domain.bo;

import lombok.Data;

import java.time.LocalDateTime;

/** Worker 错误回传请求。 */
@Data
public class ReaderSourceWorkerErrorBo {
    private Long runId;
    private String runToken;
    private String workerId;
    private String errorType;
    private Integer httpStatus;
    private String sourceUrl;
    private String message;
    private LocalDateTime retryAt;
}
