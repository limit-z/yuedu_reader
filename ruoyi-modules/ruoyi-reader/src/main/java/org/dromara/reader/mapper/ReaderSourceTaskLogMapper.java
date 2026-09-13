package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceTaskLog;

/** 采集任务事件日志数据访问层。 */
@Mapper
public interface ReaderSourceTaskLogMapper extends BaseMapper<ReaderSourceTaskLog> {
}
