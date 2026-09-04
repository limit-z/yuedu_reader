package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderNovelChapterAdminVo 相关业务能力。
 */
@Data
public class ReaderNovelChapterAdminVo implements Serializable {

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
    /**
     * 正文内容。
     */
    private String content;
}
