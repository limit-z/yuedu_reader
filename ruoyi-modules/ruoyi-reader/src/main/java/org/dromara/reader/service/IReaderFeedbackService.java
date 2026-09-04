package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderFeedbackQueryBo;
import org.dromara.reader.domain.bo.ReaderFeedbackReplyBo;
import org.dromara.reader.domain.bo.ReaderFeedbackStatusBo;
import org.dromara.reader.domain.bo.ReaderFeedbackSubmitBo;
import org.dromara.reader.domain.vo.admin.ReaderFeedbackAdminVo;
import org.dromara.reader.domain.vo.app.ReaderFeedbackRecordVo;

import java.util.List;

/**
 * 阅读器反馈服务接口，负责承接用户反馈提交、用户侧查询与管理端工单处理。
 */
public interface IReaderFeedbackService {

    /**
     * 查询当前读者自己的反馈记录。
     */
    List<ReaderFeedbackRecordVo> listMyFeedback();

    /**
     * 提交一条新的反馈记录。
     */
    Long submitFeedback(ReaderFeedbackSubmitBo bo);

    /**
     * 管理端分页查询反馈列表。
     */
    PageResult<ReaderFeedbackAdminVo> queryAdminPageList(ReaderFeedbackQueryBo bo, PageQuery pageQuery);

    /**
     * 管理员回复指定反馈工单。
     */
    void reply(Long feedbackId, ReaderFeedbackReplyBo bo);

    /**
     * 管理员更新指定反馈工单状态。
     */
    void updateStatus(Long feedbackId, ReaderFeedbackStatusBo bo);
}
