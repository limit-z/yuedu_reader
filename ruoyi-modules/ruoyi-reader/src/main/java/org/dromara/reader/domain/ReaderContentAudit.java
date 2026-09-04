package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderContentAudit 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_content_audit")
public class ReaderContentAudit extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 审核状态。
     */
    private String auditStatus;
    /**
     * 审核意见。
     */
    private String auditComment;
    /**
     * auditor标识。
     */
    private Long auditorId;
}
