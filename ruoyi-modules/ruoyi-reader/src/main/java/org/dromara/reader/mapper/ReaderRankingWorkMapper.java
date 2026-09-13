package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderRankingWork;

/** 阅读器榜单作品编排数据访问层。 */
@Mapper
public interface ReaderRankingWorkMapper extends BaseMapper<ReaderRankingWork> {
}
