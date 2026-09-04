package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 访问策略新增或修改参数。 */
@Data
public class ReaderSourcePolicyBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String policyName;
    private Integer concurrencyLimit;
    private Integer minDelayMs;
    private Integer maxDelayMs;
    private Integer requestsPerMinute;
    private Integer dailyRequestLimit;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer maxRetries;
    private Integer circuitBreakerThreshold;
    private String honorRetryAfter;
    private String remark;
}
