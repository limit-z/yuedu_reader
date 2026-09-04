package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 来源章节快照，用于增量判断和人工差异审核。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_chapter_snapshot")
public class ReaderSourceChapterSnapshot extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long taskId;
    private String sourceChapterId;
    private String sourceUrl;
    private Integer chapterNo;
    private String chapterName;
    private String contentHash;
    private String titleHash;
    private String content;
    private String snapshotStatus;
    private LocalDateTime capturedAt;
}
