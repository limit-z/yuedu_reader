package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读积分中心视图对象。
 */
@Data
public class AppPointsVo {

    /**
     * 连续签到天数。
     */
    private Integer days;

    /**
     * 今日是否已领取签到积分。
     */
    private Boolean claimed;

    /**
     * 今日已得积分。
     */
    private Integer todayPoints;

    /**
     * 累计积分。
     */
    private Integer totalPoints;

    /**
     * 阅读任务列表。
     */
    private List<AppPointsTaskVo> tasks;

    /**
     * 已完成任务标识集合。
     */
    private List<String> completedTaskKeys;

    /**
     * 阅读成就/兑换项。
     */
    private List<AppPointsRewardVo> rewards;
}
