package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderWorkQueryBo 相关业务能力。
 */
@Data
public class ReaderWorkQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词。
     */
    private String keyword;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 发布状态。
     */
    private String publishStatus;
    /**
     * 来源类型。
     */
    private String sourceType;
}
