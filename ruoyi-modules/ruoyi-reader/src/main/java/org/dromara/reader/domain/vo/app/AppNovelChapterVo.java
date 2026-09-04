package org.dromara.reader.domain.vo.app;

import lombok.Data;

/**
 * 阅读器模块代码，承载 AppNovelChapterVo 相关业务能力。
 */
@Data
public class AppNovelChapterVo {

    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 正文内容。
     */
    private String content;
    /**
     * 字数。
     */
    private Integer wordCount;
}
