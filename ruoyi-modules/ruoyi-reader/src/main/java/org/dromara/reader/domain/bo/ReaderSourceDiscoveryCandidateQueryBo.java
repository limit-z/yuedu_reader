package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 候选书源查询参数。 */
@Data
public class ReaderSourceDiscoveryCandidateQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String discoveryStatus;
    private String candidateHost;
    private String keyword;
}
