package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;
import org.dromara.reader.service.IReaderSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 书源采集任务和运行记录管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/tasks")
public class ReaderSourceTaskController {

    private final IReaderSourceService sourceService;

    @GetMapping("/list")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceTask>> list(ReaderSourceTaskQueryBo bo, PageQuery pageQuery) {
        return R.ok(sourceService.queryTaskPage(bo, pageQuery));
    }

    @PostMapping
    @SaCheckPermission("reader:source:list")
    public R<Long> add(@RequestBody ReaderSourceTaskBo bo) {
        return R.ok(sourceService.saveTask(bo));
    }

    @GetMapping("/{taskId}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderSourceTask> detail(@PathVariable Long taskId) {
        return R.ok(sourceService.getTask(taskId));
    }

    @PostMapping("/{taskId}/start")
    @SaCheckPermission("reader:source:list")
    public R<Long> start(@PathVariable Long taskId) {
        return R.ok(sourceService.startTask(taskId));
    }

    @PostMapping("/{taskId}/pause")
    @SaCheckPermission("reader:source:list")
    public R<Void> pause(@PathVariable Long taskId) {
        sourceService.pauseTask(taskId);
        return R.ok();
    }

    @PostMapping("/{taskId}/resume")
    @SaCheckPermission("reader:source:list")
    public R<Long> resume(@PathVariable Long taskId) {
        return R.ok(sourceService.resumeTask(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    @SaCheckPermission("reader:source:list")
    public R<Void> cancel(@PathVariable Long taskId) {
        sourceService.cancelTask(taskId);
        return R.ok();
    }

    @GetMapping("/{taskId}/runs")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceTaskRunAdminVo>> runs(@PathVariable Long taskId, PageQuery pageQuery) {
        return R.ok(sourceService.queryTaskRuns(taskId, pageQuery));
    }

    @GetMapping("/{taskId}/diffs")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceChapterSnapshot>> diffs(@PathVariable Long taskId,
                                                             @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                                                             PageQuery pageQuery) {
        return R.ok(sourceService.querySnapshots(taskId, status, pageQuery));
    }

    @GetMapping("/{taskId}/errors")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceError>> errors(@PathVariable Long taskId,
                                                    @org.springframework.web.bind.annotation.RequestParam(required = false) String resolved,
                                                    PageQuery pageQuery) {
        return R.ok(sourceService.queryErrors(taskId, resolved, pageQuery));
    }
}
