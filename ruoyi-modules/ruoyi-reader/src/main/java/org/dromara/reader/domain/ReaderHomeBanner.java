package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器首页横幅实体，负责承接书城顶部轮播推荐位的配置数据。
 */
@Data
@TableName("reader_home_banner")
@EqualsAndHashCode(callSuper = true)
public class ReaderHomeBanner extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 横幅主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 横幅标题。
     */
    private String bannerTitle;

    /**
     * 横幅副标题。
     */
    private String bannerSubtitle;

    /**
     * 横幅图片地址。
     */
    private String imageUrl;

    /**
     * 横幅背景色。
     */
    private String backgroundColor;

    /**
     * 跳转类型。
     */
    private String targetType;

    /**
     * 跳转值。
     */
    private String targetValue;

    /**
     * 排序值。
     */
    private Integer sortNo;

    /**
     * 状态：1启用、0停用。
     */
    private String status;

    /**
     * 生效开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 生效结束时间。
     */
    private LocalDateTime endTime;
}
