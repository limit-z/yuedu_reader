package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** H5 读者用户管理视图。 */
@Data
public class ReaderUserAdminVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long accountId;
    private String nickName;
    private String gender;
    private String region;
    private String status;
    private String lastClientType;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
