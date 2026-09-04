package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读统计类别占比。
 */
@Data
public class AppReadingStatsCategoryVo {

    /**
     * 类别名称。
     */
    private String name;

    /**
     * 占比。
     */
    private Integer percent;
}
