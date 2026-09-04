package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读器模块代码，承载 AppHomeBannerVo 相关业务能力。
 */
@Data
public class AppHomeBannerVo {

    /**
     * 横幅ID。
     */
    private Long bannerId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 图片地址。
     */
    private String imageUrl;
    /**
     * 跳转目标类型。
     */
    private String targetType;
    /**
     * 跳转目标值。
     */
    private String targetValue;
}
