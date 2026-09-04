package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceTask;

/** 书源采集任务数据访问。 */
@Mapper
public interface ReaderSourceTaskMapper extends BaseMapper<ReaderSourceTask> {
}
