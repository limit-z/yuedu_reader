package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderProgressBo 相关业务能力。
 */
@Data
public class ReaderProgressBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 页码。
     */
    private Integer pageNo;
    /**
     * 已读百分比。
     */
    private Integer readPercent;
    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 阅读进度百分比。
     */
    private Integer progressPercent;
    /**
     * 阅读定位值。
     */
    private String locationValue;
    /**
     * 客户端类型。
     */
    private String clientType;
}
