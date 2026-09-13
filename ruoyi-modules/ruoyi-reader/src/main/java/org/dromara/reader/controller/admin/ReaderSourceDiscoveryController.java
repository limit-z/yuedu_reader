package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceDiscoveryBlacklist;
import org.dromara.reader.domain.ReaderSourceDiscoveryCandidate;
import org.dromara.reader.domain.ReaderSourceDiscoveryProvider;
import org.dromara.reader.domain.ReaderSourceDiscoveryRun;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryBlacklistBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryCandidateQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryProviderBo;
import org.dromara.reader.service.IReaderSourceDiscoveryService;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 书源发现、黑名单和候选审核管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/source-discovery")
@SaCheckPermission("reader:source:list")
public class ReaderSourceDiscoveryController {

    private final IReaderSourceDiscoveryService discoveryService;

    @GetMapping("/providers")
    public R<PageResult<ReaderSourceDiscoveryProvider>> providers(@RequestParam(required = false) String providerName,
                                                                   @RequestParam(required = false) String status,
                                                                   PageQuery pageQuery) {
        return R.ok(discoveryService.queryProviderPage(providerName, status, pageQuery));
    }

    @PostMapping("/providers")
    public R<Long> addProvider(@RequestBody ReaderSourceDiscoveryProviderBo bo) {
        return R.ok(discoveryService.saveProvider(bo));
    }

    @PutMapping("/providers/{providerId}")
    public R<Void> editProvider(@PathVariable Long providerId, @RequestBody ReaderSourceDiscoveryProviderBo bo) {
        bo.setId(providerId);
        discoveryService.saveProvider(bo);
        return R.ok();
    }

    @PostMapping("/providers/{providerId}/run")
    public R<Long> runProvider(@PathVariable Long providerId) {
        return R.ok(discoveryService.runProvider(providerId));
    }

    @PostMapping("/providers/{providerId}/enable")
    public R<Void> enableProvider(@PathVariable Long providerId) {
        discoveryService.updateProviderStatus(providerId, true);
        return R.ok();
    }

    @PostMapping("/providers/{providerId}/disable")
    public R<Void> disableProvider(@PathVariable Long providerId) {
        discoveryService.updateProviderStatus(providerId, false);
        return R.ok();
    }

    /** 批量启用或停用发现源。 */
    @PostMapping("/providers/batch/{action}")
    public R<ReaderBatchActionResult> batchProviders(@PathVariable String action, @RequestBody java.util.List<Long> ids) {
        if (!"enable".equals(action) && !"disable".equals(action)) throw new IllegalArgumentException("不支持的发现源批量操作");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                discoveryService.updateProviderStatus(id, "enable".equals(action));
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    @GetMapping("/blacklist")
    public R<PageResult<ReaderSourceDiscoveryBlacklist>> blacklist(@RequestParam(required = false) String matcherType,
                                                                     @RequestParam(required = false) String status,
                                                                     PageQuery pageQuery) {
        return R.ok(discoveryService.queryBlacklistPage(matcherType, status, pageQuery));
    }

    @PostMapping("/blacklist")
    public R<Long> addBlacklist(@RequestBody ReaderSourceDiscoveryBlacklistBo bo) {
        return R.ok(discoveryService.saveBlacklist(bo));
    }

    @PutMapping("/blacklist/{blacklistId}")
    public R<Void> editBlacklist(@PathVariable Long blacklistId, @RequestBody ReaderSourceDiscoveryBlacklistBo bo) {
        bo.setId(blacklistId);
        discoveryService.saveBlacklist(bo);
        return R.ok();
    }

    @DeleteMapping("/blacklist/{blacklistId}")
    public R<Void> deleteBlacklist(@PathVariable Long blacklistId) {
        discoveryService.deleteBlacklist(blacklistId);
        return R.ok();
    }

    @PostMapping("/blacklist/{blacklistId}/enable")
    public R<Void> enableBlacklist(@PathVariable Long blacklistId) {
        discoveryService.updateBlacklistStatus(blacklistId, true);
        return R.ok();
    }

    @PostMapping("/blacklist/{blacklistId}/disable")
    public R<Void> disableBlacklist(@PathVariable Long blacklistId) {
        discoveryService.updateBlacklistStatus(blacklistId, false);
        return R.ok();
    }

    /** 批量启用、停用或删除黑名单。 */
    @PostMapping("/blacklist/batch/{action}")
    public R<ReaderBatchActionResult> batchBlacklist(@PathVariable String action, @RequestBody java.util.List<Long> ids) {
        if (!java.util.List.of("enable", "disable", "delete").contains(action)) throw new IllegalArgumentException("不支持的黑名单批量操作");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                if ("delete".equals(action)) discoveryService.deleteBlacklist(id);
                else discoveryService.updateBlacklistStatus(id, "enable".equals(action));
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    @GetMapping("/candidates")
    public R<PageResult<ReaderSourceDiscoveryCandidate>> candidates(ReaderSourceDiscoveryCandidateQueryBo bo,
                                                                      PageQuery pageQuery) {
        return R.ok(discoveryService.queryCandidatePage(bo, pageQuery));
    }

    @PostMapping("/candidates/{candidateId:\\d+}/check")
    public R<Void> checkCandidate(@PathVariable Long candidateId) {
        discoveryService.checkCandidate(candidateId);
        return R.ok();
    }

    @PostMapping("/candidates/{candidateId:\\d+}/approve")
    public R<Void> approveCandidate(@PathVariable Long candidateId) {
        discoveryService.approveCandidate(candidateId);
        return R.ok();
    }

    @PostMapping("/candidates/{candidateId:\\d+}/reject")
    public R<Void> rejectCandidate(@PathVariable Long candidateId, @RequestParam(required = false) String reason) {
        discoveryService.rejectCandidate(candidateId, reason);
        return R.ok();
    }

    /** 批量检查、通过或拒绝候选地址。 */
    @PostMapping("/candidates/batch/{action}")
    public R<ReaderBatchActionResult> batchCandidates(@PathVariable String action,
                                                       @RequestBody java.util.List<Long> ids,
                                                       @RequestParam(required = false) String reason) {
        if (!java.util.List.of("check", "approve", "reject").contains(action)) throw new IllegalArgumentException("不支持的候选批量操作");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                if ("check".equals(action)) discoveryService.checkCandidate(id);
                else if ("approve".equals(action)) discoveryService.approveCandidate(id);
                else discoveryService.rejectCandidate(id, reason);
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    @GetMapping("/runs")
    public R<PageResult<ReaderSourceDiscoveryRun>> runs(@RequestParam(required = false) Long providerId,
                                                         PageQuery pageQuery) {
        return R.ok(discoveryService.queryRunPage(providerId, pageQuery));
    }
}
