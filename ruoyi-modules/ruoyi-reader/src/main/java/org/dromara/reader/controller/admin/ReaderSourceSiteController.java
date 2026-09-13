package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteQueryBo;
import org.dromara.reader.service.IReaderSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

/** 书源站点管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/sites")
public class ReaderSourceSiteController {

    private final IReaderSourceService sourceService;

    @GetMapping("/list")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceSite>> list(ReaderSourceSiteQueryBo bo, PageQuery pageQuery) {
        return R.ok(sourceService.querySitePage(bo, pageQuery));
    }

    @PostMapping
    @SaCheckPermission("reader:source:list")
    public R<Long> add(@RequestBody ReaderSourceSiteBo bo) {
        return R.ok(sourceService.saveSite(bo));
    }

    @PutMapping("/{siteId}")
    @SaCheckPermission("reader:source:list")
    public R<Void> edit(@PathVariable Long siteId, @RequestBody ReaderSourceSiteBo bo) {
        bo.setId(siteId);
        sourceService.saveSite(bo);
        return R.ok();
    }

    @PostMapping("/{siteId}/check-compliance")
    @SaCheckPermission("reader:source:list")
    public R<Void> checkCompliance(@PathVariable Long siteId, @RequestBody ReaderSourceComplianceBo bo) {
        sourceService.checkCompliance(siteId, bo);
        return R.ok();
    }

    @PostMapping("/{siteId}/enable")
    @SaCheckPermission("reader:source:list")
    public R<Void> enable(@PathVariable Long siteId) {
        sourceService.updateSiteStatus(siteId, true);
        return R.ok();
    }

    @PostMapping("/{siteId}/disable")
    @SaCheckPermission("reader:source:list")
    public R<Void> disable(@PathVariable Long siteId) {
        sourceService.updateSiteStatus(siteId, false);
        return R.ok();
    }

    /** 批量启用或停用书源站点。 */
    @PostMapping("/batch/{action}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderBatchActionResult> batch(@PathVariable String action, @RequestBody java.util.List<Long> ids) {
        if (!"enable".equals(action) && !"disable".equals(action)) throw new IllegalArgumentException("不支持的站点批量操作");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                sourceService.updateSiteStatus(id, "enable".equals(action));
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }
}
