package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.reader.domain.ReaderUserProfile;

/**
 * 阅读器用户主表访问层，负责读写账号状态、基础资料和阅读偏好。
 */
public interface ReaderUserProfileMapper extends BaseMapper<ReaderUserProfile> {
}
