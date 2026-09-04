package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;
import org.dromara.reader.service.IReaderBookshelfService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读器应用历史控制器，负责最近阅读记录查询。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/history")
public class ReaderHistoryController {

    /**
     * 书架服务入口，历史数据当前复用同一组用户阅读服务能力。
     */
    private final IReaderBookshelfService bookshelfService;

    /**
     * 查询当前用户阅读历史。
     */
    @GetMapping
    public R<List<AppHistoryItemVo>> list() {
        return R.ok(bookshelfService.listHistory());
    }

    /**
     * 删除单条阅读历史。
     */
    @DeleteMapping("/{historyId}")
    public R<Void> remove(@PathVariable Long historyId) {
        bookshelfService.removeHistory(historyId);
        return R.ok();
    }

    /**
     * 执行 clear 相关业务逻辑。
     */
    @DeleteMapping("/clear")
    public R<Void> clear() {
        bookshelfService.clearHistory();
        return R.ok();
    }
}
