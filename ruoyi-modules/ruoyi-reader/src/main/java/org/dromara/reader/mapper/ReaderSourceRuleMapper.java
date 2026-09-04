package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceRule;

/** 书源解析规则数据访问。 */
@Mapper
public interface ReaderSourceRuleMapper extends BaseMapper<ReaderSourceRule> {
}
