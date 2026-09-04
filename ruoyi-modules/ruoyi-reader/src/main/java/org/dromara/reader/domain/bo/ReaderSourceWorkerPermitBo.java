package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 在访问第三方站点前申请一次站点访问许可。 */
@Data
public class ReaderSourceWorkerPermitBo {
    private Long runId;
    private String runToken;
    private String workerId;
}
