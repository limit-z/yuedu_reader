package org.dromara.reader.controller.worker;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.config.ReaderSourceWorkerProperties;
import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerErrorBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerPermitBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerAckVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerPermitVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerTaskVo;
import org.dromara.reader.service.IReaderSourceWorkerService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 书源采集 Worker 内部协议接口。 */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/worker/source")
public class ReaderSourceWorkerController {

    private final IReaderSourceWorkerService workerService;
    private final ReaderSourceWorkerProperties properties;

    @PostMapping("/runs/claim")
    public R<ReaderSourceWorkerTaskVo> claim(@RequestHeader("X-Reader-Worker-Secret") String secret,
                                             @RequestBody ReaderSourceWorkerClaimBo bo) {
        verifySecret(secret);
        return R.ok(workerService.claim(bo));
    }

    @PostMapping("/runs/{runId}/heartbeat")
    public R<Void> heartbeat(@RequestHeader("X-Reader-Worker-Secret") String secret,
                             @PathVariable Long runId, @RequestBody ReaderSourceWorkerHeartbeatBo bo) {
        verifySecret(secret);
        bo.setRunId(runId);
        workerService.heartbeat(bo);
        return R.ok();
    }

    @PostMapping("/runs/{runId}/permit")
    public R<ReaderSourceWorkerPermitVo> permit(@RequestHeader("X-Reader-Worker-Secret") String secret,
                                                 @PathVariable Long runId,
                                                 @RequestBody ReaderSourceWorkerPermitBo bo) {
        verifySecret(secret);
        bo.setRunId(runId);
        return R.ok(workerService.acquirePermit(bo));
    }

    @PostMapping("/runs/{runId}/result")
    public R<ReaderSourceWorkerAckVo> result(@RequestHeader("X-Reader-Worker-Secret") String secret,
                                             @PathVariable Long runId,
                                             @RequestBody ReaderSourceWorkerResultBo bo) {
        verifySecret(secret);
        bo.setRunId(runId);
        return R.ok(workerService.acceptResult(bo));
    }

    @PostMapping("/runs/{runId}/error")
    public R<Void> error(@RequestHeader("X-Reader-Worker-Secret") String secret,
                         @PathVariable Long runId, @RequestBody ReaderSourceWorkerErrorBo bo) {
        verifySecret(secret);
        bo.setRunId(runId);
        workerService.acceptError(bo);
        return R.ok();
    }

    private void verifySecret(String secret) {
        if (properties.getSharedSecret() == null || properties.getSharedSecret().isBlank()
            || secret == null || !java.security.MessageDigest.isEqual(
            properties.getSharedSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new org.dromara.common.core.exception.ServiceException("Worker 认证失败");
        }
    }
}
