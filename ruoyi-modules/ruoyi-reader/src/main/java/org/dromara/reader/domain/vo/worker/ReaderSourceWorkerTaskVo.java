package org.dromara.reader.domain.vo.worker;

import lombok.Data;

/** 下发给 Worker 的最小任务上下文，不包含管理端或站点凭据。 */
@Data
public class ReaderSourceWorkerTaskVo {
    private Long runId;
    private Long taskId;
    /** 批量任务当前领取的任务书籍明细ID。 */
    private Long taskBookId;
    /** 批量任务当前领取的本地作品ID。 */
    private Long workId;
    private Long siteId;
    private Long ruleId;
    private Integer ruleVersion;
    private String runToken;
    private String workerId;
    private String executorType;
    /** 采集模式：SINGLE单本、ALL全站、CATEGORY按分类。 */
    private String collectionMode;
    /** 按分类采集时的来源分类名称。 */
    private String categoryName;
    /** 服务端下发的批量书籍数量上限。 */
    private Integer bookLimit;
    private String sourceWorkUrl;
    private String sourceWorkTitle;
    private String authorName;
    /** 备用书源跨站匹配时使用的搜索地址模板。 */
    private String searchUrlTemplate;
    private Long fallbackId;
    private String catalogUrlTemplate;
    private String chapterUrlTemplate;
    private String selectorJson;
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
    private Integer claimLeaseSeconds;
}
