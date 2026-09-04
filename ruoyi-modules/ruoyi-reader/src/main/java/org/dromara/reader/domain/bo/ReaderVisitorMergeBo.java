package org.dromara.reader.domain.bo;

import lombok.Data;

/**
 * 阅读器访客合并入参，负责接收待合并的 visitorId。
 */
@Data
public class ReaderVisitorMergeBo {

    /**
     * 前端本地缓存的访客标识。
     */
    private String visitorId;
}
