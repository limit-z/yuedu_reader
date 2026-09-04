package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 书源站点新增或修改参数。 */
@Data
public class ReaderSourceSiteBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String siteName;
    private String baseUrl;
    private String allowedHost;
    private String authorizationNote;
    private Long defaultPolicyId;
    private String remark;
}
