package org.dromara.reader.domain.vo.worker;

import lombok.Data;

/** 站点访问许可结果。 */
@Data
public class ReaderSourceWorkerPermitVo {
    private Boolean allowed;
    private Long retryAfterMs;
    private String reason;
}
