package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderReadingCommentSubmitBo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppReadingCommentVo;
import org.dromara.reader.service.IReaderReadingCommentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读点评控制器。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading/comments")
public class ReaderReadingCommentController {

    /**
     * 点评服务。
     */
    private final IReaderReadingCommentService readingCommentService;

    /**
     * 查询点评列表。
     */
    @GetMapping
    public R<AppPageVo<AppReadingCommentVo>> list(@RequestParam(required = false) Long workId,
                                                  @RequestParam(required = false) Long chapterId,
                                                  @RequestParam(required = false) Integer pageNum,
                                                  @RequestParam(required = false) Integer pageSize) {
        return R.ok(readingCommentService.listComments(workId, chapterId, pageNum, pageSize));
    }

    /**
     * 提交点评。
     */
    @PostMapping
    public R<Long> submit(@RequestBody ReaderReadingCommentSubmitBo bo) {
        return R.ok(readingCommentService.submitComment(bo));
    }
}
