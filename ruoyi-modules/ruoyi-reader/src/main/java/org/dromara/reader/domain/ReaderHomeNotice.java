package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器首页公告实体，负责承接书城公告滚动条的文案与跳转配置。
 */
@Data
@TableName("reader_home_notice")
@EqualsAndHashCode(callSuper = true)
public class ReaderHomeNotice extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公告主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 公告标题。
     */
    private String noticeTitle;

    /**
     * 公告正文。
     */
    private String noticeContent;

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
