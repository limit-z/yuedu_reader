package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 发现出来但尚未成为正式书源站点的候选地址。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_discovery_candidate")
public class ReaderSourceDiscoveryCandidate extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long providerId;
    private String candidateUrl;
    private String candidateHost;
    private String candidateName;
    private String discoveryStatus;
    private String blacklistStatus;
    private String robotsStatus;
    private String availabilityStatus;
    private Integer httpStatus;
    private String checkMessage;
    private LocalDateTime lastCheckedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long siteId;
}
