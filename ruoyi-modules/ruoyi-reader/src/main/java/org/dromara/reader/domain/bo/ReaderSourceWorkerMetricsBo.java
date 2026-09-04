package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 回传的本批次脱敏统计。 */
@Data
public class ReaderSourceWorkerMetricsBo {
    private Integer requests;
    private Integer skipped;
    private Integer failures;
    private Integer tooManyRequests;
}
