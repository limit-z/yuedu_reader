package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderWork 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_work")
public class ReaderWork extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 内容分类，如玄幻、言情、修仙；与小说/漫画作品类型分开。
     */
    private String categoryName;
    /**
     * 作品作者，用于与标题一起生成稳定去重键。
     */
    private String authorName;
    /**
     * 规范化标题和作者的 SHA-256 去重键。
     */
    private String dedupeKey;
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
     * 横版封面地址，用于首页推荐等宽图场景。
     */
    private String coverLandscapeUrl;
    /** 封面背景模式：GLOBAL、COLOR、IMAGE。 */
    private String coverBackgroundMode;
    /** 作品级封面背景色。 */
    private String coverBackgroundColor;
    /** 作品级封面背景图片 OSS ID。 */
    private Long coverBackgroundOssId;
    /** 自动封面版本号，用于刷新浏览器缓存。 */
    private Integer coverRevision;
    /**
     * 连载状态。
     */
    private String serialStatus;
    /**
     * 发布状态。
     */
    private String publishStatus;
    /**
     * 来源类型。
     */
    private String sourceType;
    /**
     * 总章节数。
     */
    private Integer totalChapters;
    /**
     * 总页数。
     */
    private Integer totalPages;
    /**
     * 是否允许被搜索。
     */
    private String allowSearch;
}
