package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceTaskRun;

/** 书源采集运行记录数据访问。 */
@Mapper
public interface ReaderSourceTaskRunMapper extends BaseMapper<ReaderSourceTaskRun> {
}
