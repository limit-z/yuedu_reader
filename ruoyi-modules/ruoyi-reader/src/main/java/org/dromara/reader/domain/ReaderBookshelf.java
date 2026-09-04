package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderBookshelf 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_bookshelf")
public class ReaderBookshelf extends BaseEntity {

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
     * 是否置顶。
     */
    private String topPin;

    /**
     * 手动排序值，数值越小越靠前。
     */
    private Integer sortNo;
}
