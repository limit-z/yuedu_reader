package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器模块代码，承载 AppDiscoverConfigVo 相关业务能力。
 */
@Data
public class AppDiscoverConfigVo {

    /**
     * categories 字段。
     */
    private List<Item> categories;
    /**
     * rankings 字段。
     */
    private List<Item> rankings;
    /**
     * 热门关键词。
     */
    private List<String> hotKeywords;

    @Data
    public static class Item {
        /**
         * 主键ID。
         */
        private Long id;
        /**
         * name 字段。
         */
        private String name;
    }
}
