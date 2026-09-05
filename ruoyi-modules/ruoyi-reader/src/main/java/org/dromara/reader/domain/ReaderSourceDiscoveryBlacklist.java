package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 发现流程在发起任何外部请求前使用的黑名单规则。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_discovery_blacklist")
public class ReaderSourceDiscoveryBlacklist extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String matcherType;
    private String matcherValue;
    private String reason;
    private String source;
    private String status;
}
