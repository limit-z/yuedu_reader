package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 风险与授权来源确认结果，由管理员确认后写入。 */
@Data
public class ReaderSourceComplianceBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 管理员基于站点外部授权材料做出的采集许可确认，不表示平台已验证目标站点授权。
     */
    private Boolean approved;

    /** 合同、版权方、合作方、书面许可或其他外部授权依据说明。 */
    private String authorizationNote;
}
