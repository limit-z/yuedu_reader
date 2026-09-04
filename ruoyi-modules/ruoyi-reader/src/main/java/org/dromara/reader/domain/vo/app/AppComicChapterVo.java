package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器模块代码，承载 AppComicChapterVo 相关业务能力。
 */
@Data
public class AppComicChapterVo {

    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 章节名。
     */
    private String chapterName;
    /**
     * 页数。
     */
    private Integer pageCount;
    /**
     * 图片地址列表。
     */
    private List<String> imageUrls;
}
