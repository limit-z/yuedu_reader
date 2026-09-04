package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderAuditQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderAuditRecordVo;

/**
 * 阅读器模块代码，承载 IReaderAuditService 相关业务能力。
 */
public interface IReaderAuditService {

    /**
     * 审核通过指定记录。
     */
    void approve(Long auditId);

    /**
     * 按筛选条件分页查询列表数据。
     */
    PageResult<ReaderAuditRecordVo> queryPageList(ReaderAuditQueryBo bo, PageQuery pageQuery);
}
