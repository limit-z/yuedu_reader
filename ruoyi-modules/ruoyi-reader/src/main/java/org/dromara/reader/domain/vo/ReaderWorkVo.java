package org.dromara.reader.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.reader.domain.ReaderWork;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderWorkVo 相关业务能力。
 */
@Data
@AutoMapper(target = ReaderWork.class)
public class ReaderWorkVo implements Serializable {

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
