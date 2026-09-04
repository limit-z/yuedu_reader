package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读统计周数据。
 */
@Data
public class AppReadingStatsDayVo {

    /**
     * 周几标签。
     */
    private String label;

    /**
     * 估算阅读小时数。
     */
    private Double hours;
}
