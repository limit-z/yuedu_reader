package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器反馈管理视图对象，负责给后台管理端展示完整的反馈工单信息。
 */
@Data
public class ReaderFeedbackAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 反馈记录ID。
     */
    private Long feedbackId;

    /**
     * 读者主体ID。
     */
    private Long readerId;

    /**
     * 读者身份类型。
     */
    private String accountType;

    /**
     * 反馈类型。
     */
    private String feedbackType;

    /**
     * 反馈正文内容。
     */
    private String feedbackContent;

    /**
     * 提交时昵称。
     */
    private String nickName;

    /**
     * 提交时手机号。
     */
    private String contactMobile;

    /**
     * 提交时微信号。
     */
    private String contactWechat;

    /**
     * 工单处理状态。
     */
    private String status;

    /**
     * 管理员回复内容。
     */
    private String replyContent;

    /**
     * 最后回复人ID。
     */
    private Long replyBy;

    /**
     * 最后回复时间。
     */
    private LocalDateTime replyTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
