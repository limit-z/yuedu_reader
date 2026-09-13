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
    /**
     * 所属任务书籍明细ID，单本旧任务允许为空。
     */
    private Long taskBookId;
    /**
     * 归属的本地作品ID，便于从快照回溯作品。
     */
    private Long workId;
    /**
     * 所属运行批次ID。
     */
    private Long runId;
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
