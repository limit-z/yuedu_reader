package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderFeedbackSubmitBo;
import org.dromara.reader.domain.vo.app.ReaderFeedbackRecordVo;
import org.dromara.reader.service.IReaderFeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读器应用反馈控制器，负责用户侧意见反馈查询与提交。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/feedback")
public class ReaderFeedbackController {

    /**
     * 反馈服务入口，负责用户侧工单列表与提单动作。
     */
    private final IReaderFeedbackService readerFeedbackService;

    /**
     * 查询当前读者自己的反馈记录。
     */
    @GetMapping
    public R<List<ReaderFeedbackRecordVo>> list() {
        return R.ok(readerFeedbackService.listMyFeedback());
    }

    /**
     * 提交一条新的反馈记录。
     */
    @PostMapping
    public R<Long> submit(@RequestBody ReaderFeedbackSubmitBo bo) {
        return R.ok(readerFeedbackService.submitFeedback(bo));
    }
}

