package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderBookshelfBatchBo;
import org.dromara.reader.domain.vo.app.AppBookshelfItemVo;
import org.dromara.reader.service.IReaderBookshelfService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 阅读器应用书架控制器，负责书架增删查接口。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/bookshelf")
public class ReaderBookshelfController {

    /**
     * 书架服务入口，负责承接当前用户书架相关操作。
     */
    private final IReaderBookshelfService bookshelfService;

    /**
     * 查询当前用户书架列表。
     */
    @GetMapping
    public R<List<AppBookshelfItemVo>> list(@RequestParam(required = false) String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return R.ok(bookshelfService.listBookshelf());
        }
        return R.ok(bookshelfService.listBookshelf(sortBy));
    }

    /**
     * 批量移出书架。
     */
    @PostMapping("/batch-remove")
    public R<Void> batchRemove(@RequestBody ReaderBookshelfBatchBo bo) {
        bookshelfService.batchRemove(bo.getWorkIds());
        return R.ok();
    }

    /**
     * 置顶或取消置顶作品。
     */
    @PutMapping("/{workId}/pin")
    public R<Void> pin(@PathVariable Long workId, @RequestParam(defaultValue = "true") boolean pinned) {
        bookshelfService.setTopPin(workId, pinned);
        return R.ok();
    }

    /**
     * 书架顺序重排。
     */
    @PostMapping("/reorder")
    public R<Void> reorder(@RequestBody ReaderBookshelfBatchBo bo) {
        bookshelfService.reorder(bo.getWorkIds());
        return R.ok();
    }
}
