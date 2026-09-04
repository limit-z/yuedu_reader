package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 合规检查结果，由管理员确认后写入。 */
@Data
public class ReaderSourceComplianceBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Boolean approved;
    private String authorizationNote;
}
