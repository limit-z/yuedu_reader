package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderReadingHistory 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_reading_history")
public class ReaderReadingHistory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 用户ID。
     */
    private Long userId;
    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 内容类型。
     */
    private String contentType;
    /**
     * 阅读定位值。
     */
    private String locationValue;
    /**
     * 客户端类型。
     */
    private String clientType;
    /**
     * 阅读时间。
     */
    private LocalDateTime readAt;
}
