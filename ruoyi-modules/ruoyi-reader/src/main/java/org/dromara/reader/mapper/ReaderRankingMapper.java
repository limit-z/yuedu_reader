package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderRanking;

/** 阅读器榜单配置数据访问层。 */
@Mapper
public interface ReaderRankingMapper extends BaseMapper<ReaderRanking> {
}
