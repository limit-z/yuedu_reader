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
    /** 采集模式：SINGLE单本、ALL全站、CATEGORY按分类。 */
    private String collectionMode;
    /** 批量采集最多处理的书籍数量；单本任务固定为 1。 */
    private Integer bookLimit;
    /** 按分类采集时的来源分类名称。 */
    private String categoryName;
}
