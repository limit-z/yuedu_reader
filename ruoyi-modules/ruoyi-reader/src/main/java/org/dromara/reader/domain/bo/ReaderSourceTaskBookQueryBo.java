package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采集任务书籍明细分页筛选参数。
 */
@Data
public class ReaderSourceTaskBookQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细状态。 */
    private String status;
    /** 去重动作。 */
    private String dedupeAction;
    /** 标题或作者关键字。 */
    private String keyword;
}
