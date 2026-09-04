package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器反馈提交业务对象，负责承接读者提交的意见反馈表单。
 */
@Data
public class ReaderFeedbackSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 反馈类型。
     */
    private String feedbackType;

    /**
     * 反馈正文内容。
     */
    private String feedbackContent;
}

