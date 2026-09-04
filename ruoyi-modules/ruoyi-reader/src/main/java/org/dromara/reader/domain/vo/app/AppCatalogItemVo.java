package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读器模块代码，承载 AppCatalogItemVo 相关业务能力。
 */
@Data
public class AppCatalogItemVo {

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
}
