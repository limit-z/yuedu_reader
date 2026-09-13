package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.reader.domain.ReaderWorkCategory;

/**
 * 阅读器作品分类数据访问层。
 */
@Mapper
public interface ReaderWorkCategoryMapper extends BaseMapper<ReaderWorkCategory> {
}
