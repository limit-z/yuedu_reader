package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderWorkBo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderWorkQueryBo;
import org.dromara.reader.domain.vo.ReaderWorkVo;
import org.dromara.reader.domain.vo.admin.ReaderCatalogAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderComicChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderNovelChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.service.IReaderWorkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读器管理端作品控制器，提供作品列表、详情、目录预览与上下架接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/works")
public class ReaderWorkController {

    /**
     * 作品管理服务入口，负责作品、目录、预览与上下架动作承接。
     */
    private final IReaderWorkService readerWorkService;

    /**
     * 创建作品草稿记录。
     */
    @PostMapping
    public R<Long> add(@RequestBody ReaderWorkBo bo) {
        return R.ok(readerWorkService.createWork(bo));
    }

    /**
     * 分页查询作品列表。
     */
    @GetMapping("/list")
    public R<PageResult<ReaderWorkVo>> list(ReaderWorkQueryBo bo, PageQuery pageQuery) {
        return R.ok(readerWorkService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询详情数据。
     */
    @GetMapping("/{workId}")
    public R<ReaderWorkDetailAdminVo> detail(@PathVariable Long workId) {
        return R.ok(readerWorkService.queryDetail(workId));
    }

    /**
     * 查询目录数据。
     */
    @GetMapping("/{workId}/catalog")
    public R<List<ReaderCatalogAdminVo>> catalog(@PathVariable Long workId) {
        return R.ok(readerWorkService.queryCatalog(workId));
    }

    /**
     * 查询小说章节内容。
     */
    @GetMapping("/novels/{chapterId}")
    public R<ReaderNovelChapterAdminVo> novelChapter(@PathVariable Long chapterId) {
        return R.ok(readerWorkService.queryNovelChapter(chapterId));
    }

    /**
     * 查询漫画章节内容。
     */
    @GetMapping("/comics/{chapterId}")
    public R<ReaderComicChapterAdminVo> comicChapter(@PathVariable Long chapterId) {
        return R.ok(readerWorkService.queryComicChapter(chapterId));
    }

    /**
     * 上架作品并同步章节状态。
     */
    @PutMapping("/{workId}/publish")
    public R<Void> publish(@PathVariable Long workId) {
        readerWorkService.publish(workId);
        return R.ok();
    }

    /**
     * 下架作品并同步章节状态。
     */
    @PutMapping("/{workId}/offline")
    public R<Void> offline(@PathVariable Long workId) {
        readerWorkService.offline(workId);
        return R.ok();
    }
}
