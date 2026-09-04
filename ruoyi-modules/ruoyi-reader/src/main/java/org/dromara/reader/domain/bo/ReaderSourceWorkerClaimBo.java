package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 领取任务请求。 */
@Data
public class ReaderSourceWorkerClaimBo {
    private String executorType;
    private String workerId;
}
