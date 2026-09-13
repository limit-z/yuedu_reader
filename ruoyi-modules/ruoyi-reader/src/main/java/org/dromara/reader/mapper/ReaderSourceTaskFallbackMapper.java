package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceTaskFallback;

/** 采集任务备用书源路由数据访问层。 */
@Mapper
public interface ReaderSourceTaskFallbackMapper extends BaseMapper<ReaderSourceTaskFallback> {
}
