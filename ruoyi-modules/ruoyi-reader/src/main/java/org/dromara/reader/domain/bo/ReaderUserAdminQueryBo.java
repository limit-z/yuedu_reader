package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** H5 读者用户筛选参数。 */
@Data
public class ReaderUserAdminQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String keyword;
    private String status;
    private String lastClientType;
}
