package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardChapterVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkDetailVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkVo;
import org.dromara.reader.service.IReaderSourceDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 书源采集中心大盘和小说维度报表接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/dashboard")
public class ReaderSourceDashboardController {

    private final IReaderSourceDashboardService dashboardService;

    @GetMapping("/overview")
    @SaCheckPermission("reader:source:list")
    public R<ReaderSourceDashboardVo> overview(
        @RequestParam(required = false, defaultValue = "14") int days) {
        return R.ok(dashboardService.overview(days));
    }

    @GetMapping("/works")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceDashboardWorkVo>> works(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String categoryName,
        @RequestParam(required = false) String status,
        PageQuery pageQuery) {
        return R.ok(dashboardService.queryWorks(keyword, categoryName, status, pageQuery));
    }

    @GetMapping("/works/{workId}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderSourceDashboardWorkDetailVo> workDetail(@PathVariable Long workId) {
        return R.ok(dashboardService.getWorkDetail(workId));
    }

    @GetMapping("/works/{workId}/chapters")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceDashboardChapterVo>> workChapters(
        @PathVariable Long workId,
        @RequestParam(required = false) String contentStatus,
        PageQuery pageQuery) {
        return R.ok(dashboardService.queryWorkChapters(workId, contentStatus, pageQuery));
    }
}
