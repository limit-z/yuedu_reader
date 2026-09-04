package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器用户反馈实体，负责承接小程序“意见反馈”与后台工单处理闭环。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_user_feedback")
public class ReaderUserFeedback extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 反馈记录主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 读者主体ID，登录态为读者账号ID，游客态为访客账户ID。
     */
    private Long readerId;

    /**
     * 读者身份类型：USER登录用户、VISITOR游客。
     */
    private String accountType;

    /**
     * 反馈类型，如功能异常、内容问题、体验建议等。
     */
    private String feedbackType;

    /**
     * 反馈正文内容。
     */
    private String feedbackContent;

    /**
     * 提交时带上的手机号快照。
     */
    private String contactMobile;

    /**
     * 提交时带上的微信号快照。
     */
    private String contactWechat;

    /**
     * 提交时使用的昵称快照。
     */
    private String nickName;

    /**
     * 工单处理状态。
     */
    private String status;

    /**
     * 管理员回复内容。
     */
    private String replyContent;

    /**
     * 最后回复的管理员用户ID。
     */
    private Long replyBy;

    /**
     * 最后回复时间。
     */
    private LocalDateTime replyTime;
}
