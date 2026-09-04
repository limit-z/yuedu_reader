package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderReadingBookmarkSaveBo;
import org.dromara.reader.domain.vo.app.AppReadingBookmarkVo;
import org.dromara.reader.service.IReaderReadingBookmarkService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读书签控制器。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading/bookmarks")
public class ReaderReadingBookmarkController {

    /**
     * 书签服务。
     */
    private final IReaderReadingBookmarkService bookmarkService;

    /**
     * 查询书签列表。
     */
    @GetMapping
    public R<List<AppReadingBookmarkVo>> list(@RequestParam(required = false) Long workId) {
        return R.ok(bookmarkService.listBookmarks(workId));
    }

    /**
     * 新增书签。
     */
    @PostMapping
    public R<Long> save(@RequestBody ReaderReadingBookmarkSaveBo bo) {
        return R.ok(bookmarkService.saveBookmark(bo));
    }

    /**
     * 删除书签。
     */
    @DeleteMapping("/{bookmarkId}")
    public R<Void> remove(@PathVariable Long bookmarkId) {
        bookmarkService.removeBookmark(bookmarkId);
        return R.ok();
    }
}
