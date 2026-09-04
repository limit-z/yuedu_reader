package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourcePolicy;

/** 书源访问策略数据访问。 */
@Mapper
public interface ReaderSourcePolicyMapper extends BaseMapper<ReaderSourcePolicy> {
}
