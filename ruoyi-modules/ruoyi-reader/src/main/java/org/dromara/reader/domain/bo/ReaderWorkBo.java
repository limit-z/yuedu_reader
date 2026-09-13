package org.dromara.reader.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.reader.domain.ReaderWork;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderWorkBo 相关业务能力。
 */
@Data
@AutoMapper(target = ReaderWork.class, reverseConvertGenerate = false)
public class ReaderWorkBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    private Long id;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 内容分类，如玄幻、言情、修仙；与小说/漫画作品类型分开。
     */
    private String categoryName;
    /** 作品作者，用于和标题组成唯一去重键。 */
    private String authorName;
    /**
     * 标题。
     */
    private String title;
    /**
     * 简介内容。
     */
    private String intro;
    /**
     * 封面地址。
     */
    private String coverUrl;
    /**
     * 横版封面地址。
     */
    private String coverLandscapeUrl;
    private String coverBackgroundMode;
    private String coverBackgroundColor;
    private Long coverBackgroundOssId;
}
