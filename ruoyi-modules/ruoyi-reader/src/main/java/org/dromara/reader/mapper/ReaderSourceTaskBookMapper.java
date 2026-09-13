package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderSourceTaskBook;

/**
 * 采集任务书籍明细数据访问层。
 */
@Mapper
public interface ReaderSourceTaskBookMapper extends BaseMapper<ReaderSourceTaskBook> {
}
