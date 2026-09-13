package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourceTaskFallback;
import org.dromara.reader.domain.ReaderSourceTaskLog;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBookQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.service.IReaderSourceService;
import org.dromara.reader.service.IReaderSourceWorkerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 书源采集任务和运行记录管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/tasks")
public class ReaderSourceTaskController {

    private final IReaderSourceService sourceService;
    private final IReaderSourceWorkerService workerService;

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

    /** 补偿已采集快照，确保历史采集结果进入作品管理和内容审核池。 */
    @PostMapping("/{taskId}/materialize")
    @SaCheckPermission("reader:source:list")
    public R<Integer> materialize(@PathVariable Long taskId) {
        return R.ok(workerService.materializePending(taskId));
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

    /** 管理员确认后重置站点熔断计数并重新创建运行记录。 */
    @PostMapping("/{taskId}/retry-circuit")
    @SaCheckPermission("reader:source:list")
    public R<Long> retryCircuit(@PathVariable Long taskId) {
        return R.ok(sourceService.retryCircuitOpenTask(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    @SaCheckPermission("reader:source:list")
    public R<Void> cancel(@PathVariable Long taskId) {
        sourceService.cancelTask(taskId);
        return R.ok();
    }

    /** 批量执行采集任务动作，逐条遵守额度、熔断、租约和状态校验。 */
    @PostMapping("/batch/{action}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderBatchActionResult> batch(@PathVariable String action, @RequestBody List<Long> taskIds) {
        if (!List.of("start", "pause", "resume", "cancel", "retry-circuit").contains(action)) {
            throw new IllegalArgumentException("不支持的采集任务批量操作");
        }
        return R.ok(ReaderBatchActionResult.execute(taskIds, id -> {
            try {
                switch (action) {
                    case "start" -> sourceService.startTask(id);
                    case "pause" -> sourceService.pauseTask(id);
                    case "resume" -> sourceService.resumeTask(id);
                    case "cancel" -> sourceService.cancelTask(id);
                    case "retry-circuit" -> sourceService.retryCircuitOpenTask(id);
                    default -> throw new IllegalArgumentException("不支持的采集任务批量操作");
                }
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
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

    /** 查询任务的备用书源路由。 */
    @GetMapping("/{taskId}/fallbacks")
    @SaCheckPermission("reader:source:list")
    public R<java.util.List<ReaderSourceTaskFallback>> fallbacks(@PathVariable Long taskId) {
        return R.ok(sourceService.queryTaskFallbacks(taskId));
    }

    /** 手动补偿已有任务的自动备用源编排，正常 401/403 时也会由 Worker 自动调用。 */
    @PostMapping("/{taskId}/fallbacks/auto-provision")
    @SaCheckPermission("reader:source:list")
    public R<Integer> autoProvisionFallbacks(@PathVariable Long taskId) {
        return R.ok(sourceService.autoProvisionTaskFallbacks(taskId));
    }

    @GetMapping("/{taskId}/children")
    @SaCheckPermission("reader:source:list")
    public R<java.util.List<ReaderSourceTask>> children(@PathVariable Long taskId) {
        return R.ok(sourceService.queryTaskChildren(taskId));
    }

    /** 保存任务的备用书源路由。 */
    @PostMapping("/{taskId}/fallbacks")
    @SaCheckPermission("reader:source:list")
    public R<Long> saveFallback(@PathVariable Long taskId, @RequestBody ReaderSourceTaskFallback fallback) {
        fallback.setTaskId(taskId);
        return R.ok(sourceService.saveTaskFallback(fallback));
    }

    /** 查询任务的全链路自动化日志。 */
    @GetMapping("/{taskId}/logs")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceTaskLog>> logs(@PathVariable Long taskId, PageQuery pageQuery) {
        return R.ok(sourceService.queryTaskLogs(taskId, pageQuery));
    }

    /** 查询批量任务中的书籍明细和逐书进度。 */
    @GetMapping("/{taskId}/books")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceTaskBook>> books(@PathVariable Long taskId, ReaderSourceTaskBookQueryBo bo,
                                                      PageQuery pageQuery) {
        return R.ok(sourceService.queryTaskBooks(taskId, bo, pageQuery));
    }

    /** 查询单本采集明细。 */
    @GetMapping("/{taskId}/books/{taskBookId}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderSourceTaskBook> book(@PathVariable Long taskId, @PathVariable Long taskBookId) {
        return R.ok(sourceService.getTaskBook(taskId, taskBookId));
    }

    /** 查询单本采集明细的章节快照和正文预览。 */
    @GetMapping("/{taskId}/books/{taskBookId}/snapshots")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceChapterSnapshot>> bookSnapshots(@PathVariable Long taskId,
                                                                      @PathVariable Long taskBookId,
                                                                      PageQuery pageQuery) {
        return R.ok(sourceService.queryTaskBookSnapshots(taskId, taskBookId, pageQuery));
    }
}
