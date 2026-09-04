package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 书源站点查询参数。 */
@Data
public class ReaderSourceSiteQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String siteName;
    private String complianceStatus;
    private String status;
}
