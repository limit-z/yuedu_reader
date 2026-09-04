package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 心跳请求。 */
@Data
public class ReaderSourceWorkerHeartbeatBo {
    private Long runId;
    private String runToken;
    private String workerId;
}
