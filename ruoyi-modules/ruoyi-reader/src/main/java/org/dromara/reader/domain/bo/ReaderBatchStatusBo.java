package org.dromara.reader.domain.bo;

import lombok.Data;

import java.util.List;

/** 管理端批量状态变更请求。 */
@Data
public class ReaderBatchStatusBo {
    private List<Long> ids;
    private String status;
}
