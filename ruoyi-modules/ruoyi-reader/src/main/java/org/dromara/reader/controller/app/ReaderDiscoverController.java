package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppDiscoverConfigVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.service.IReaderPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器应用发现页控制器，输出分类、推荐位等发现页数据。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/discover")
public class ReaderDiscoverController {

    /**
     * 门户服务入口，负责发现页配置与作品检索结果输出。
     */
    private final IReaderPortalService portalService;

    /**
     * 查询发现页配置。
     */
    @GetMapping("/config")
    public R<AppDiscoverConfigVo> config() {
        return R.ok(portalService.getDiscoverConfig());
    }

    /**
     * 按发现页筛选条件查询作品列表。
     */
    @GetMapping("/works")
    public R<AppPageVo<AppWorkCardVo>> works(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String workType,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Integer pageNum,
                                             @RequestParam(required = false) Integer pageSize) {
        return R.ok(portalService.searchWorks(keyword, workType, categoryId, pageNum, pageSize));
    }
}
