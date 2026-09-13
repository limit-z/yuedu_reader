package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 已下载并上传的封面候选图，保留外部来源用于追溯。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_cover_candidate")
public class ReaderCoverCandidate extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long crawlTaskId;
    private Long workId;
    private String orientation;
    private String sourceProvider;
    private String sourceQuery;
    private String sourcePageUrl;
    private String sourceImageUrl;
    private Long uploadedOssId;
    private String storedImageUrl;
    private Integer width;
    private Integer height;
    private String contentHash;
    private String status;
    private String failureReason;
    private LocalDateTime capturedAt;
}
