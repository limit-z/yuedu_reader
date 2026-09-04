package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppRankingVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.service.IReaderRankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读排行榜控制器。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/rankings")
public class ReaderRankingController {

    /**
     * 排行榜服务。
     */
    private final IReaderRankingService rankingService;

    /**
     * 查询排行榜配置。
     */
    @GetMapping
    public R<List<AppRankingVo>> listRankings() {
        return R.ok(rankingService.listRankings());
    }

    /**
     * 查询指定排行榜作品。
     */
    @GetMapping("/{rankingKey}/works")
    public R<AppPageVo<AppWorkCardVo>> listWorks(@PathVariable String rankingKey,
                                                 @RequestParam(required = false) Integer pageNum,
                                                 @RequestParam(required = false) Integer pageSize) {
        return R.ok(rankingService.listRankingWorks(rankingKey, pageNum, pageSize));
    }
}
