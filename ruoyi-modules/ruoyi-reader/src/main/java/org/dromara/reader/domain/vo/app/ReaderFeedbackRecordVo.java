package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器用户反馈记录视图对象，负责给小程序反馈页回显自己的工单记录。
 */
@Data
public class ReaderFeedbackRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 反馈记录ID。
     */
    private Long feedbackId;

    /**
     * 反馈类型。
     */
    private String feedbackType;

    /**
     * 反馈正文内容。
     */
    private String feedbackContent;

    /**
     * 反馈提交时的昵称快照。
     */
    private String nickName;

    /**
     * 反馈提交时的手机号快照。
     */
    private String contactMobile;

    /**
     * 反馈提交时的微信号快照。
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
     * 回复时间。
     */
    private LocalDateTime replyTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}

