package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 采集错误记录，只保存脱敏后的诊断信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_error")
public class ReaderSourceError extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long taskId;
    private Long runId;
    private String errorType;
    private Integer httpStatus;
    private String sourceUrl;
    private String message;
    private LocalDateTime retryAt;
    private String resolved;
}
