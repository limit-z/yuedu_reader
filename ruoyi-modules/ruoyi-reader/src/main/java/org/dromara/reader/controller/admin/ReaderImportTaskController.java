package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderImportTaskBo;
import org.dromara.reader.domain.bo.ReaderImportTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderImportTaskAdminVo;
import org.dromara.reader.service.IReaderImportTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器管理端导入任务控制器，提供任务创建与列表查询接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/import/tasks")
public class ReaderImportTaskController {

    /**
     * 导入任务服务入口，负责任务建档与导入列表查询。
     */
    private final IReaderImportTaskService importTaskService;

    /**
     * 创建数据记录。
     */
    @PostMapping
    public R<Long> create(@RequestBody ReaderImportTaskBo bo) {
        return R.ok(importTaskService.createTask(bo));
    }

    /**
     * 分页查询导入任务列表。
     */
    @GetMapping("/list")
    public R<PageResult<ReaderImportTaskAdminVo>> list(ReaderImportTaskQueryBo bo, PageQuery pageQuery) {
        return R.ok(importTaskService.queryPageList(bo, pageQuery));
    }
}
