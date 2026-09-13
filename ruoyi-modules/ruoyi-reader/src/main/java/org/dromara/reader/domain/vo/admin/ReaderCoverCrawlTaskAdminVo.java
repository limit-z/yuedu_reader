package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReaderCoverCrawlTaskAdminVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long workId;
    private String status;
    private Integer portraitTargetCount;
    private Integer landscapeTargetCount;
    private Integer portraitSuccessCount;
    private Integer landscapeSuccessCount;
    private String currentProvider;
    private Integer progressPercent;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ReaderCoverCandidateAdminVo> candidates;
}
