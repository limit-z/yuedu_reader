package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器模块代码，承载 AppPageVo 相关业务能力。
 */
@Data
public class AppPageVo<T> {

    /**
     * 数据列表。
     */
    private List<T> list;
    /**
     * 总记录数。
     */
    private Long total;
    /**
     * 页码。
     */
    private Integer pageNum;
    /**
     * 每页条数。
     */
    private Integer pageSize;
    /**
     * 总页数。
     */
    private Integer totalPages;
}
