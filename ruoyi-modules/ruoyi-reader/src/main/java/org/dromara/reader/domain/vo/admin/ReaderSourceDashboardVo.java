package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 书源采集中心总览统计。所有指标均由当前业务表实时聚合。 */
@Data
public class ReaderSourceDashboardVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime generatedAt;
    private Long taskTotal;
    private Long activeTaskTotal;
    private Long runTotal;
    private Long runningRunTotal;
    private Long bookTotal;
    private Long workTotal;
    private Long chapterTotal;
    private Long contentReadyChapterTotal;
    private Long plannedChapterTotal;
    private Long processedChapterTotal;
    private Long successChapterTotal;
    private Long skippedChapterTotal;
    private Long failedChapterTotal;
    private Long pendingChapterTotal;
    private Long snapshotTotal;
    private Long errorTotal;
    private Long unresolvedErrorTotal;
    private Long taskLogTotal;
    private Long enabledSiteTotal;
    private Long approvedSiteTotal;
    private Long policyTotal;
    private Long activePolicyTotal;
    private Long ruleTotal;
    private Long activeRuleTotal;
    private Long fallbackTotal;
    private Long discoveryProviderTotal;
    private Long discoveryCandidateTotal;
    private Long coverTaskTotal;
    private Long coverCompletedTotal;
    private Long coverRunningTotal;
    private Long coverFailedTotal;

    private Map<String, Long> taskStatusCounts = new LinkedHashMap<>();
    private Map<String, Long> runStatusCounts = new LinkedHashMap<>();
    private Map<String, Long> bookStatusCounts = new LinkedHashMap<>();
    private Map<String, Long> failureCodeCounts = new LinkedHashMap<>();
    private Map<String, Long> errorTypeCounts = new LinkedHashMap<>();
    private Map<String, Long> executorCounts = new LinkedHashMap<>();
    private Map<String, Long> triggerCounts = new LinkedHashMap<>();
    private List<SiteStat> siteStats = List.of();
    private List<TrendStat> recentTrend = List.of();
    private List<CountItem> recentFailures = List.of();

    @Data
    public static class CountItem implements Serializable {
        private String key;
        private String label;
        private Long count;
    }

    @Data
    public static class SiteStat implements Serializable {
        private Long siteId;
        private String siteName;
        private String allowedHost;
        private String status;
        private String complianceStatus;
        private Long taskCount;
        private Long runningTaskCount;
        private Long bookCount;
        private Long successChapterCount;
        private Long failedChapterCount;
        private Long requestCount;
        private Long runCount;
    }

    @Data
    public static class TrendStat implements Serializable {
        private LocalDate date;
        private Long runCount;
        private Long successCount;
        private Long failureCount;
        private Long requestCount;
    }
}
