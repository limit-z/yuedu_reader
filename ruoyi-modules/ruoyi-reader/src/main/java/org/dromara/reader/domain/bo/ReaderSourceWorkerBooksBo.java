package org.dromara.reader.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * Worker 批量任务发现结果，服务端会在这里完成作品去重和任务入队。
 */
@Data
public class ReaderSourceWorkerBooksBo {
    /** 运行记录ID，由路径参数写入。 */
    private Long runId;
    /** 运行令牌。 */
    private String runToken;
    /** Worker 标识。 */
    private String workerId;
    /** 发现结果批次标识，用于 Redis 幂等。 */
    private String batchId;
    /** 来源作品列表。 */
    private List<ReaderSourceWorkerBookBo> books;
    /** 是否已完成目录发现。 */
    private Boolean completed;
}
