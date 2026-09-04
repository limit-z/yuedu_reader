package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 阅读器模块代码，承载 ReaderComicChapterAdminVo 相关业务能力。
 */
@Data
public class ReaderComicChapterAdminVo implements Serializable {

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
    /**
     * 图片地址列表。
     */
    private List<String> imageUrls;
}
