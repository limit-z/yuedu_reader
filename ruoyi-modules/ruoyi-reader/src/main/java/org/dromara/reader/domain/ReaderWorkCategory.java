package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器作品内容分类，和小说、漫画作品类型分开维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_work_category")
public class ReaderWorkCategory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类主键。 */
    @TableId
    private Long id;
    /** 展示名称。 */
    private String categoryName;
    /** 用于唯一判断的规范化名称。 */
    private String normalizedName;
    /** 分类来源：MANUAL 手工、SOURCE 书源发现。 */
    private String sourceType;
    /** 状态：1启用、0停用。 */
    private String status;
}
