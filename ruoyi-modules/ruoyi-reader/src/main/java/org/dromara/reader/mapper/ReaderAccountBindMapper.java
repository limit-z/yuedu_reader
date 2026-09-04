package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.reader.domain.ReaderAccountBind;

/**
 * 阅读器账号绑定访问层，负责手机号、邮箱和微信标识到系统用户的映射。
 */
public interface ReaderAccountBindMapper extends BaseMapper<ReaderAccountBind> {
}
