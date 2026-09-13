package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderWorkDetailAdminVo 相关业务能力。
 */
@Data
public class ReaderWorkDetailAdminVo implements Serializable {

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
     * 作品内容分类。
     */
    private String categoryName;
    /** 作品作者。 */
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
    private String coverBackgroundImageUrl;
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
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 最新审核状态。
     */
    private String auditStatus;
}
