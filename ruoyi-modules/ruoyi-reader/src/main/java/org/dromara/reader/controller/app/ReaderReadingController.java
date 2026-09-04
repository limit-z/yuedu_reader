package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.domain.vo.app.AppComicChapterVo;
import org.dromara.reader.domain.vo.app.AppNovelChapterVo;
import org.dromara.reader.domain.vo.app.AppReadingProgressVo;
import org.dromara.reader.service.IReaderAppContentService;
import org.dromara.reader.service.IReaderProgressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器应用阅读控制器，负责阅读正文获取与阅读进度同步。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading")
public class ReaderReadingController {

    /**
     * 内容服务入口，负责提供阅读正文与目录相关内容。
     */
    private final IReaderAppContentService appContentService;

    /**
     * 进度服务入口，负责接收并查询阅读进度。
     */
    private final IReaderProgressService progressService;

    /**
     * 查询小说章节正文。
     */
    @GetMapping("/novels/{chapterId}")
    public R<AppNovelChapterVo> novelChapter(@PathVariable Long chapterId,
                                             @RequestParam(required = false) Long workId) {
        return R.ok(appContentService.getNovelChapter(workId, chapterId));
    }

    /**
     * 查询漫画章节图片列表。
     */
    @GetMapping("/comics/{chapterId}")
    public R<AppComicChapterVo> comicChapter(@PathVariable Long chapterId,
                                             @RequestParam(required = false) Long workId) {
        return R.ok(appContentService.getComicChapter(workId, chapterId));
    }

    /**
     * 保存作品阅读进度。
     */
    @PostMapping("/progress")
    public R<Void> saveProgress(@RequestBody ReaderProgressBo bo) {
        progressService.saveProgress(bo);
        return R.ok();
    }

    /**
     * 查询作品阅读进度。
     */
    @GetMapping("/progress/{workId}")
    public R<AppReadingProgressVo> getProgress(@PathVariable Long workId) {
        return R.ok(progressService.getProgress(workId));
    }
}
