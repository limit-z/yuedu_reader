package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 书源访问限流和熔断策略。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_policy")
public class ReaderSourcePolicy extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
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
    private String status;
    private String remark;
}
