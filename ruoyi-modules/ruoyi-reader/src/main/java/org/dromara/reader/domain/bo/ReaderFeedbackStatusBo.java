package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器反馈状态更新业务对象，负责承接管理员对工单状态的独立流转操作。
 */
@Data
public class ReaderFeedbackStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标状态：PENDING、PROCESSING、REPLIED、DONE。
     */
    private String status;
}

