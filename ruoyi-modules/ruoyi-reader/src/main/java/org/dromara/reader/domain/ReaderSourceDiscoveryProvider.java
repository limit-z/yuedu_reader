package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 管理员明确配置的公开索引或授权 Feed。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_discovery_provider")
public class ReaderSourceDiscoveryProvider extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String providerName;
    private String providerUrl;
    private String providerType;
    private String authorizationNote;
    private Integer pollIntervalSeconds;
    private Integer requestIntervalMs;
    private Integer maxCandidates;
    private String status;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private String lastRunStatus;
    private String lastError;
}
