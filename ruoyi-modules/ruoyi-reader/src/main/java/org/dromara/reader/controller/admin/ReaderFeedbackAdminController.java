package org.dromara.reader.controller.admin;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderFeedbackQueryBo;
import org.dromara.reader.domain.bo.ReaderFeedbackReplyBo;
import org.dromara.reader.domain.bo.ReaderFeedbackStatusBo;
import org.dromara.reader.domain.vo.admin.ReaderFeedbackAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.domain.bo.ReaderBatchStatusBo;
import org.dromara.reader.service.IReaderFeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器反馈管理控制器，负责后台查看、回复与流转反馈工单。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/feedback")
public class ReaderFeedbackAdminController {

    /**
     * 反馈服务入口，负责管理端反馈工单查询与处理。
     */
    private final IReaderFeedbackService readerFeedbackService;

    /**
     * 分页查询反馈工单列表。
     */
    @GetMapping("/list")
    public R<PageResult<ReaderFeedbackAdminVo>> list(ReaderFeedbackQueryBo bo, PageQuery pageQuery) {
        return R.ok(readerFeedbackService.queryAdminPageList(bo, pageQuery));
    }

    /**
     * 管理员回复指定反馈工单。
     */
    @PutMapping("/{feedbackId}/reply")
    public R<Void> reply(@PathVariable Long feedbackId, @RequestBody ReaderFeedbackReplyBo bo) {
        readerFeedbackService.reply(feedbackId, bo);
        return R.ok();
    }

    /**
     * 管理员更新指定反馈工单状态。
     */
    @PutMapping("/{feedbackId}/status")
    public R<Void> status(@PathVariable Long feedbackId, @RequestBody ReaderFeedbackStatusBo bo) {
        readerFeedbackService.updateStatus(feedbackId, bo);
        return R.ok();
    }

    /** 批量更新工单状态，失败项会保留原始业务错误。 */
    @PostMapping("/batch/status")
    public R<ReaderBatchActionResult> batchStatus(@RequestBody ReaderBatchStatusBo bo) {
        ReaderFeedbackStatusBo statusBo = new ReaderFeedbackStatusBo();
        statusBo.setStatus(bo.getStatus());
        return R.ok(ReaderBatchActionResult.execute(bo.getIds(), feedbackId -> {
            try {
                readerFeedbackService.updateStatus(feedbackId, statusBo);
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }
}
