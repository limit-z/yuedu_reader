package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.service.IReaderSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 书源限流策略管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/policies")
public class ReaderSourcePolicyController {

    private final IReaderSourceService sourceService;

    @GetMapping("/list")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourcePolicy>> list(@RequestParam(required = false) String policyName,
                                                   @RequestParam(required = false) String status,
                                                   PageQuery pageQuery) {
        return R.ok(sourceService.queryPolicyPage(policyName, status, pageQuery));
    }

    @PostMapping
    @SaCheckPermission("reader:source:list")
    public R<Long> add(@RequestBody ReaderSourcePolicyBo bo) {
        return R.ok(sourceService.savePolicy(bo));
    }

    @PutMapping("/{policyId}")
    @SaCheckPermission("reader:source:list")
    public R<Void> edit(@PathVariable Long policyId, @RequestBody ReaderSourcePolicyBo bo) {
        bo.setId(policyId);
        sourceService.savePolicy(bo);
        return R.ok();
    }

    /** 批量启用或停用限流策略，逐条返回状态校验失败原因。 */
    @PostMapping("/batch/{action}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderBatchActionResult> batchStatus(@PathVariable String action, @RequestBody java.util.List<Long> policyIds) {
        if (!"enable".equals(action) && !"disable".equals(action)) {
            throw new IllegalArgumentException("不支持的限流策略批量操作");
        }
        return R.ok(sourceService.batchPolicyStatus(policyIds, "enable".equals(action)));
    }
}
