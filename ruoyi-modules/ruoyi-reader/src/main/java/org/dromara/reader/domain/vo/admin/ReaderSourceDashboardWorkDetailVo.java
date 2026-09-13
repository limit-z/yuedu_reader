package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 小说维度的采集全链路详情。 */
@Data
public class ReaderSourceDashboardWorkDetailVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long workId;
    private String title;
    private String authorName;
    private String categoryName;
    private String serialStatus;
    private String publishStatus;
    private Long currentTaskId;
    private String currentTaskName;
    private String currentTaskStatus;
    private Long taskCount;
    private Long bookRecordCount;
    private Long chapterTotal;
    private Long contentReadyChapterCount;
    private Long contentMissingChapterCount;
    private Long plannedChapterCount;
    private Long processedChapterCount;
    private Long successChapterCount;
    private Long skippedChapterCount;
    private Long failedChapterCount;
    private Integer progressPercent;
    private Map<String, Long> taskStatusCounts = new LinkedHashMap<>();
    private Map<String, Long> bookStatusCounts = new LinkedHashMap<>();
    private Map<String, Long> chapterStatusCounts = new LinkedHashMap<>();
    private List<ReaderSourceDashboardTaskVo> tasks = List.of();
    private List<ReaderSourceDashboardErrorVo> recentErrors = List.of();
    private List<ReaderSourceDashboardLogVo> recentLogs = List.of();
}
