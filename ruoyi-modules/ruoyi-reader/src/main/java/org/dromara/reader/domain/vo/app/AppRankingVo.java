package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 排行榜配置视图对象。
 */
@Data
public class AppRankingVo {

    /**
     * 榜单标识。
     */
    private String rankingKey;

    /**
     * 榜单名称。
     */
    private String rankingName;

    /**
     * 榜单说明。
     */
    private String rankingDesc;

    /** 榜单模式：AUTO自动、MANUAL手工。 */
    private String rankingMode;

    /** 自动榜单排序规则。 */
    private String sortRule;

    /** 展示顺序。 */
    private Integer sortNo;
}
