package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器反馈回复业务对象，负责承接管理员回复内容与可选状态变更。
 */
@Data
public class ReaderFeedbackReplyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 管理员回复内容。
     */
    private String replyContent;

    /**
     * 回复后希望切换到的状态，缺省时保持现有状态。
     */
    private String status;
}

