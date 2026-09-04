package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 小说章节元数据实体，负责承载目录、序号、字数和发布状态等轻量信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_novel_chapter")
public class ReaderNovelChapter extends BaseEntity {

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
     * 卷名。
     */
    private String volumeName;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 章节序号。
     */
    private Integer chapterNo;
    /**
     * 字数。
     */
    private Integer wordCount;
    /**
     * 发布状态。
     */
    private String publishStatus;
}
