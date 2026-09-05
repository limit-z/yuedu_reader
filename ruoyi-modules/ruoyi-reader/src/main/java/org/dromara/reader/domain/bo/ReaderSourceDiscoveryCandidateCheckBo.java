package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 候选检查参数预留对象，保持接口可扩展。 */
@Data
public class ReaderSourceDiscoveryCandidateCheckBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
