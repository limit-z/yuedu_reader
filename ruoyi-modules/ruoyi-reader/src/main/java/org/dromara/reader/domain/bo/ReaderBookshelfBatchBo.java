package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 阅读器模块代码，承载 ReaderBookshelfBatchBo 相关业务能力。
 */
@Data
public class ReaderBookshelfBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * work标识列表。
     */
    private List<Long> workIds;
}
