package org.dromara.reader.domain.vo.worker;

import lombok.Data;

/** 下发给 Worker 的最小任务上下文，不包含管理端或站点凭据。 */
@Data
public class ReaderSourceWorkerTaskVo {
    private Long runId;
    private Long taskId;
    private Long siteId;
    private Long ruleId;
    private Integer ruleVersion;
    private String runToken;
    private String workerId;
    private String executorType;
    private String sourceWorkUrl;
    private String sourceWorkTitle;
    private Integer cursorChapterNo;
    private Integer startChapterNo;
    private Integer endChapterNo;
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
}
