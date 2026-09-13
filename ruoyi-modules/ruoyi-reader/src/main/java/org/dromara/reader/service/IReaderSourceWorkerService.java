package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerErrorBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerPermitBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerBooksBo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerAckVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerPermitVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerTaskVo;

/** 采集 Worker 统一协议服务。 */
public interface IReaderSourceWorkerService {
    ReaderSourceWorkerTaskVo claim(ReaderSourceWorkerClaimBo bo);

    /** Recover runs whose worker heartbeat has expired and create resumable replacement runs. */
    int recoverStaleRuns();

    /** Rebuild task-level book counters from the authoritative task-book rows. */
    int synchronizeTaskProgress();

    void heartbeat(ReaderSourceWorkerHeartbeatBo bo);

    ReaderSourceWorkerPermitVo acquirePermit(ReaderSourceWorkerPermitBo bo);

    ReaderSourceWorkerAckVo acceptResult(ReaderSourceWorkerResultBo bo);

    void acceptError(ReaderSourceWorkerErrorBo bo);

    /** 接收批量任务的来源书籍发现结果并执行服务端去重。 */
    ReaderSourceWorkerAckVo acceptBooks(ReaderSourceWorkerBooksBo bo);

    /** 批量任务领取下一本待处理书籍。 */
    ReaderSourceWorkerTaskVo claimNextBook(Long runId, String runToken, String workerId);

    /** 将已采集但尚未建档的章节快照补偿写入作品、章节和审核池。 */
    int materializePending(Long taskId);
}
