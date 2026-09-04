package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读积分任务视图对象。
 */
@Data
public class AppPointsTaskVo {

    /**
     * 任务标识。
     */
    private String key;

    /**
     * 任务名称。
     */
    private String title;

    /**
     * 任务说明。
     */
    private String note;

    /**
     * 奖励积分。
     */
    private Integer reward;

    /**
     * 操作文案。
     */
    private String action;

    /**
     * 配图地址。
     */
    private String art;

    /**
     * 是否已完成。
     */
    private Boolean completed;
}
