package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 书源发现运行审计记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_discovery_run")
public class ReaderSourceDiscoveryRun extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long providerId;
    private String runToken;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer candidateCount;
    private Integer blockedCount;
    private Integer robotsDeniedCount;
    private Integer availableCount;
    private Integer failedCount;
    private String errorMessage;
}
