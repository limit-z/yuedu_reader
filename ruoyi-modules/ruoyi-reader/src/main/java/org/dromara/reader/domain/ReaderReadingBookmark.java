package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器书签实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_reading_bookmark")
public class ReaderReadingBookmark extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 读者主体ID。
     */
    private Long userId;

    /**
     * 作品ID。
     */
    private Long workId;

    /**
     * 作品标题快照。
     */
    private String workTitle;

    /**
     * 作品类型。
     */
    private String workType;

    /**
     * 章节ID。
     */
    private Long chapterId;

    /**
     * 章节名快照。
     */
    private String chapterName;

    /**
     * 章节序号快照。
     */
    private Integer chapterNo;

    /**
     * 阅读定位值。
     */
    private String locationValue;
}
