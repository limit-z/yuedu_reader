package org.dromara.reader.domain.vo.worker;

import lombok.Data;

/** Worker 回传确认结果。 */
@Data
public class ReaderSourceWorkerAckVo {
    private Long runId;
    private Integer acceptedCount;
    private Integer skippedCount;
    private Integer cursorChapterNo;
    private String runStatus;
    private String taskStatus;
}
