package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderAuditQueryBo 相关业务能力。
 */
@Data
public class ReaderAuditQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审核状态。
     */
    private String auditStatus;
    /**
     * 作品标题。
     */
    private String workTitle;
}
