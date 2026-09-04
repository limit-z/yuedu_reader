package org.dromara.reader.domain.bo;

import lombok.Data;

import java.util.List;

/** Worker 章节结果批次请求。 */
@Data
public class ReaderSourceWorkerResultBo {
    private Long runId;
    private String runToken;
    private String workerId;
    private String executorType;
    private String batchId;
    private Integer ruleVersion;
    private Integer cursorChapterNo;
    private Boolean completed;
    private List<ReaderSourceWorkerItemBo> items;
    private ReaderSourceWorkerMetricsBo metrics;
}
