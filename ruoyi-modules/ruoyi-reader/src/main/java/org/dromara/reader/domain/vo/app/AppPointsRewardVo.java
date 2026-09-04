package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读积分奖励项视图对象。
 */
@Data
public class AppPointsRewardVo {

    /**
     * 奖励名称。
     */
    private String title;

    /**
     * 兑换所需积分。
     */
    private Integer cost;

    /**
     * 奖励说明。
     */
    private String desc;
}
