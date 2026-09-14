package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** H5 书评筛选参数。 */
@Data
public class ReaderReadingCommentAdminQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String keyword;
    private String status;
    private Long workId;
}
