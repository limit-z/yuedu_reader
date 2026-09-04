package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.service.IReaderPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读器应用搜索控制器，负责作品搜索能力对外暴露。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/search")
public class ReaderSearchController {

    /**
     * 门户服务入口，负责搜索建议、热词与搜索结果查询。
     */
    private final IReaderPortalService portalService;

    /**
     * 查询热门搜索词。
     */
    @GetMapping("/hot-keywords")
    public R<List<String>> hotKeywords() {
        return R.ok(portalService.getDiscoverConfig().getHotKeywords());
    }

    /**
     * 查询搜索建议。
     */
    @GetMapping("/suggest")
    public R<List<String>> suggest(@RequestParam String keyword) {
        List<String> suggest = portalService.searchWorks(keyword, null, null, 1, 8)
            .getList()
            .stream()
            .map(AppWorkCardVo::getTitle)
            .toList();
        return R.ok(suggest);
    }

    /**
     * 按关键词及筛选条件搜索作品列表。
     */
    @GetMapping
    public R<AppPageVo<AppWorkCardVo>> search(@RequestParam String keyword,
                                              @RequestParam(required = false) String workType,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        return R.ok(portalService.searchWorks(keyword, workType, categoryId, pageNum, pageSize));
    }
}
