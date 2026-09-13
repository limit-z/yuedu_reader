package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReaderSourceDashboardLogVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private Long runId;
    private Long taskBookId;
    private String level;
    private String eventType;
    private String message;
    private String detailJson;
    private LocalDateTime eventAt;
}
