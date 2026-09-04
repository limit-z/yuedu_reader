package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器反馈查询业务对象，负责管理端按状态、类型和关键词筛选反馈工单。
 */
@Data
public class ReaderFeedbackQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工单处理状态。
     */
    private String status;

    /**
     * 反馈类型。
     */
    private String feedbackType;

    /**
     * 关键词，匹配昵称、反馈正文、联系方式等字段。
     */
    private String keyword;
}

