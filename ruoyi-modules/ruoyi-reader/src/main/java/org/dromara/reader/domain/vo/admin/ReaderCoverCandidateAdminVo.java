package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReaderCoverCandidateAdminVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String orientation;
    private String sourceProvider;
    private String sourceQuery;
    private String sourcePageUrl;
    private String sourceImageUrl;
    private Long uploadedOssId;
    private String storedImageUrl;
    private Integer width;
    private Integer height;
    private String status;
    private String failureReason;
    private LocalDateTime capturedAt;
}
