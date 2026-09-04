package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderAuditRecordVo 相关业务能力。
 */
@Data
public class ReaderAuditRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    private Long id;
    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 作品标题。
     */
    private String workTitle;
    /**
     * 审核状态。
     */
    private String auditStatus;
    /**
     * 审核意见。
     */
    private String auditComment;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
