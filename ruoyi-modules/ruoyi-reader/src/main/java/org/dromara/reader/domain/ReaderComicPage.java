package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderComicPage 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_comic_page")
public class ReaderComicPage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 页码。
     */
    private Integer pageNo;
    /**
     * 图片地址。
     */
    private String imageUrl;
    /**
     * width 字段。
     */
    private Integer width;
    /**
     * height 字段。
     */
    private Integer height;
}
