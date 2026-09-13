package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.service.IReaderSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

/** 书源解析规则管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source/rules")
public class ReaderSourceRuleController {

    private final IReaderSourceService sourceService;

    @GetMapping("/list")
    @SaCheckPermission("reader:source:list")
    public R<PageResult<ReaderSourceRule>> list(@RequestParam(required = false) Long siteId,
                                                @RequestParam(required = false) String status,
                                                PageQuery pageQuery) {
        return R.ok(sourceService.queryRulePage(siteId, status, pageQuery));
    }

    @PostMapping
    @SaCheckPermission("reader:source:list")
    public R<Long> add(@RequestBody ReaderSourceRuleBo bo) {
        return R.ok(sourceService.saveRule(bo));
    }

    @PutMapping("/{ruleId}")
    @SaCheckPermission("reader:source:list")
    public R<Void> edit(@PathVariable Long ruleId, @RequestBody ReaderSourceRuleBo bo) {
        bo.setId(ruleId);
        sourceService.saveRule(bo);
        return R.ok();
    }

    @PostMapping("/{ruleId}/publish")
    @SaCheckPermission("reader:source:list")
    public R<Void> publish(@PathVariable Long ruleId) {
        sourceService.updateRuleStatus(ruleId, true);
        return R.ok();
    }

    @PostMapping("/{ruleId}/disable")
    @SaCheckPermission("reader:source:list")
    public R<Void> disable(@PathVariable Long ruleId) {
        sourceService.updateRuleStatus(ruleId, false);
        return R.ok();
    }

    /** 批量发布或停用解析规则。 */
    @PostMapping("/batch/{action}")
    @SaCheckPermission("reader:source:list")
    public R<ReaderBatchActionResult> batch(@PathVariable String action, @RequestBody java.util.List<Long> ids) {
        if (!"publish".equals(action) && !"disable".equals(action)) throw new IllegalArgumentException("不支持的规则批量操作");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                sourceService.updateRuleStatus(id, "publish".equals(action));
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }
}
