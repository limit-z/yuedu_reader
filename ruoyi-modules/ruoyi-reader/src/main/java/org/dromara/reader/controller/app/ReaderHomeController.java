package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppHomeVo;
import org.dromara.reader.domain.vo.app.AppTopicDetailVo;
import org.dromara.reader.service.IReaderPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器应用首页控制器，输出首页横幅、栏目与公告数据。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/home")
public class ReaderHomeController {

    /**
     * 门户服务入口，负责首页聚合数据输出。
     */
    private final IReaderPortalService portalService;

    /**
     * 获取首页聚合数据。
     */
    @GetMapping
    public R<AppHomeVo> home() {
        return R.ok(portalService.getHome());
    }

    /**
     * 获取单个专题详情。
     */
    @GetMapping("/topics/{topicKey}")
    public R<AppTopicDetailVo> topicDetail(@PathVariable String topicKey) {
        return R.ok(portalService.getTopicDetail(topicKey));
    }
}
