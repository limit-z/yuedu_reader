package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读统计视图对象。
 */
@Data
public class AppReadingStatsVo {

    /**
     * 本月已读作品数。
     */
    private Integer monthReadBooks;

    /**
     * 累计已读作品数。
     */
    private Integer totalReadBooks;

    /**
     * 已读完作品数。
     */
    private Integer finishedBooks;

    /**
     * 估算总阅读小时数。
     */
    private Double estimatedReadHours;

    /**
     * 日均阅读小时数。
     */
    private Double averageDailyHours;

    /**
     * 本月目标作品数。
     */
    private Integer monthTargetBooks;

    /**
     * 目标完成度。
     */
    private Integer progressPercent;

    /**
     * 本周阅读曲线。
     */
    private List<AppReadingStatsDayVo> weeklyHours;

    /**
     * 类别分布。
     */
    private List<AppReadingStatsCategoryVo> categoryDistribution;

    /**
     * 最近阅读记录。
     */
    private List<AppHistoryItemVo> recentReads;
}
