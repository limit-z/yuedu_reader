package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 黑名单新增或修改参数。 */
@Data
public class ReaderSourceDiscoveryBlacklistBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String matcherType;
    private String matcherValue;
    private String reason;
    private String source;
}
