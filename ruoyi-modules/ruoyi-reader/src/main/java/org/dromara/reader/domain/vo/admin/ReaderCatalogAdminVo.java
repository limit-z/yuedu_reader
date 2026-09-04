package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderCatalogAdminVo 相关业务能力。
 */
@Data
public class ReaderCatalogAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 章节序号。
     */
    private Integer chapterNo;
    /**
     * 卷名。
     */
    private String volumeName;
    /**
     * 字数。
     */
    private Integer wordCount;
    /**
     * 页数。
     */
    private Integer pageCount;
    /**
     * 发布状态。
     */
    private String publishStatus;
    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
