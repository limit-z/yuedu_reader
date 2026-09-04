package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppReadingStatsVo;
import org.dromara.reader.service.IReaderReadingStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读统计控制器。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading/stats")
public class ReaderReadingStatsController {

    /**
     * 统计服务。
     */
    private final IReaderReadingStatsService readingStatsService;

    /**
     * 查询阅读统计。
     */
    @GetMapping
    public R<AppReadingStatsVo> stats() {
        return R.ok(readingStatsService.getStats());
    }
}
