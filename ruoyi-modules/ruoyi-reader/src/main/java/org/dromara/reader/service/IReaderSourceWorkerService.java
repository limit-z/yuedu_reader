package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderSourceWorkerClaimBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerErrorBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerHeartbeatBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerPermitBo;
import org.dromara.reader.domain.bo.ReaderSourceWorkerResultBo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerAckVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerPermitVo;
import org.dromara.reader.domain.vo.worker.ReaderSourceWorkerTaskVo;

/** 采集 Worker 统一协议服务。 */
public interface IReaderSourceWorkerService {
    ReaderSourceWorkerTaskVo claim(ReaderSourceWorkerClaimBo bo);

    void heartbeat(ReaderSourceWorkerHeartbeatBo bo);

    ReaderSourceWorkerPermitVo acquirePermit(ReaderSourceWorkerPermitBo bo);

    ReaderSourceWorkerAckVo acceptResult(ReaderSourceWorkerResultBo bo);

    void acceptError(ReaderSourceWorkerErrorBo bo);
}
