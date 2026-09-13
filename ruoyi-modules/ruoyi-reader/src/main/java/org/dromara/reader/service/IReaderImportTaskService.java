package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderImportTaskBo;
import org.dromara.reader.domain.bo.ReaderImportTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderImportTaskAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

import java.util.List;

/**
 * 阅读器模块代码，承载 IReaderImportTaskService 相关业务能力。
 */
public interface IReaderImportTaskService {

    /**
     * 创建导入任务并立即触发文件解析。
     */
    Long createTask(ReaderImportTaskBo bo);

    /**
     * 按筛选条件分页查询列表数据。
     */
    PageResult<ReaderImportTaskAdminVo> queryPageList(ReaderImportTaskQueryBo bo, PageQuery pageQuery);

    /**
     * 批量取消或重试解析任务。
     */
    ReaderBatchActionResult batchAction(List<Long> taskIds, String action);
}
