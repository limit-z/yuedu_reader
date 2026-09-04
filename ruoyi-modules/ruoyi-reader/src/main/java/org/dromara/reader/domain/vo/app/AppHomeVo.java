package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器模块代码，承载 AppHomeVo 相关业务能力。
 */
@Data
public class AppHomeVo {

    /**
     * 首页横幅列表。
     */
    private List<AppHomeBannerVo> banners;
    /**
     * 首页公告列表。
     */
    private List<AppHomeNoticeVo> notices;
    /**
     * 首页栏目列表。
     */
    private List<AppHomeSectionVo> sections;
}
