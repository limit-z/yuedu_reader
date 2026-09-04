package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 书源采集任务新增参数。 */
@Data
public class ReaderSourceTaskBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String taskName;
    private Long siteId;
    private Long ruleId;
    private Long policyId;
    private String executorType;
    private String sourceWorkUrl;
    private String sourceWorkTitle;
    private Integer startChapterNo;
    private Integer endChapterNo;
    private String incremental;
}
