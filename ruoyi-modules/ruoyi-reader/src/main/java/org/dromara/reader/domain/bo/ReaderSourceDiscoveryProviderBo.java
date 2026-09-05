package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 发现源新增或修改参数。 */
@Data
public class ReaderSourceDiscoveryProviderBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String providerName;
    private String providerUrl;
    private String providerType;
    private String authorizationNote;
    private Integer pollIntervalSeconds;
    private Integer requestIntervalMs;
    private Integer maxCandidates;
}
