package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderComicChapter 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_comic_chapter")
public class ReaderComicChapter extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 作品ID。
     */
    private Long workId;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 章节序号。
     */
    private Integer chapterNo;
    /**
     * 页数。
     */
    private Integer pageCount;
    /**
     * 发布状态。
     */
    private String publishStatus;
}
