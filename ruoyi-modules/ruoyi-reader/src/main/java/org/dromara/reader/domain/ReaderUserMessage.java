package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器用户站内消息实体，负责承接消息中心列表与已读状态回写。
 */
@Data
@TableName("reader_user_message")
@EqualsAndHashCode(callSuper = true)
public class ReaderUserMessage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 站内消息主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 读者主体ID。
     */
    private Long readerId;

    /**
     * 读者身份类型。
     */
    private String accountType;

    /**
     * 消息类型。
     */
    private String messageType;

    /**
     * 消息标题。
     */
    private String title;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 业务关联键。
     */
    private String bizKey;

    /**
     * 跳转类型。
     */
    private String linkType;

    /**
     * 跳转值。
     */
    private String linkValue;

    /**
     * 已读状态：0未读、1已读。
     */
    private String readStatus;

    /**
     * 首次已读时间。
     */
    private LocalDateTime readTime;
}
