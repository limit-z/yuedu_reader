package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 书源站点配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_site")
public class ReaderSourceSite extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String siteName;
    private String baseUrl;
    private String allowedHost;
    private String authorizationNote;
    private String complianceStatus;
    private LocalDateTime complianceCheckedAt;
    private Long complianceCheckedBy;
    private String status;
    private Long defaultPolicyId;
    private String remark;
}
