package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 书源采集任务查询参数。 */
@Data
public class ReaderSourceTaskQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String taskName;
    private Long siteId;
    private String executorType;
    private String status;
    /** 采集模式筛选。 */
    private String collectionMode;
}
