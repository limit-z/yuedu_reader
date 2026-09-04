package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderImportTaskQueryBo 相关业务能力。
 */
@Data
public class ReaderImportTaskQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导入任务名称。
     */
    private String taskName;
    /**
     * 当前状态。
     */
    private String status;
}
