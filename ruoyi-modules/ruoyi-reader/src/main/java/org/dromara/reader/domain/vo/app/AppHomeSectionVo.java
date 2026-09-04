package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器模块代码，承载 AppHomeSectionVo 相关业务能力。
 */
@Data
public class AppHomeSectionVo {

    /**
     * 业务编码。
     */
    private String code;
    /**
     * 标题。
     */
    private String title;
    /**
     * 更多页路径。
     */
    private String morePath;
    /**
     * 数据项列表。
     */
    private List<AppWorkCardVo> items;
}
