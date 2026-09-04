package org.dromara.reader.controller.admin;

import org.dromara.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderAuditQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderAuditRecordVo;
import org.dromara.reader.service.IReaderAuditService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器管理端审核控制器，提供待审记录查询与审核操作。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/audits")
public class ReaderAuditController {

    /**
     * 审核服务入口，负责承接管理端审核列表与审核通过动作。
     */
    private final IReaderAuditService readerAuditService;

    /**
     * 分页查询待审记录列表。
     */
    @GetMapping("/list")
    public R<PageResult<ReaderAuditRecordVo>> list(ReaderAuditQueryBo bo, PageQuery pageQuery) {
        return R.ok(readerAuditService.queryPageList(bo, pageQuery));
    }

    /**
     * 审核通过指定记录。
     */
    @PostMapping("/{auditId}/approve")
    public R<Void> approve(@PathVariable Long auditId) {
        readerAuditService.approve(auditId);
        return R.ok();
    }
}
