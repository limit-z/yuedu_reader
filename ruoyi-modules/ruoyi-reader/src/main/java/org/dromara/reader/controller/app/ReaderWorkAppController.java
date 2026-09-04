package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppCatalogItemVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.dromara.reader.service.IReaderAppContentService;
import org.dromara.reader.service.IReaderBookshelfService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读器应用作品控制器，提供作品详情、目录和章节阅读入口。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/works")
public class ReaderWorkAppController {

    /**
     * 内容服务入口，负责详情与目录等作品内容读取。
     */
    private final IReaderAppContentService appContentService;

    /**
     * 书架服务入口，负责作品与用户书架关系维护。
     */
    private final IReaderBookshelfService bookshelfService;

    /**
     * 查询作品详情。
     */
    @GetMapping("/{workId}")
    public R<AppWorkDetailVo> detail(@PathVariable Long workId) {
        return R.ok(appContentService.getWorkDetail(workId));
    }

    /**
     * 查询作品目录。
     */
    @GetMapping("/{workId}/catalog")
    public R<List<AppCatalogItemVo>> catalog(@PathVariable Long workId) {
        return R.ok(appContentService.getCatalog(workId));
    }

    /**
     * 分页查询作品目录，读者端默认按 20 章一批加载。
     */
    @GetMapping("/{workId}/catalog/page")
    public R<AppPageVo<AppCatalogItemVo>> catalogPage(@PathVariable Long workId,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        return R.ok(appContentService.getCatalogPage(workId, pageNum, pageSize));
    }

    /**
     * 查询相关推荐。
     */
    @GetMapping("/{workId}/recommendations")
    public R<List<AppWorkCardVo>> recommendations(@PathVariable Long workId) {
        return R.ok(appContentService.getRecommendations(workId));
    }

    /**
     * 加入书架。
     */
    @PostMapping("/{workId}/bookshelf")
    public R<Void> addToBookshelf(@PathVariable Long workId) {
        bookshelfService.add(workId);
        return R.ok();
    }

    /**
     * 移出书架。
     */
    @DeleteMapping("/{workId}/bookshelf")
    public R<Void> removeFromBookshelf(@PathVariable Long workId) {
        bookshelfService.remove(workId);
        return R.ok();
    }
}
