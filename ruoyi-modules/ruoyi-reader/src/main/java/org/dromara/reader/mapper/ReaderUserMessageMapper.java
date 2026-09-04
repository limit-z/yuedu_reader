package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.reader.domain.ReaderUserMessage;

/**
 * 阅读器用户站内消息访问层，负责消息中心列表与已读状态回写。
 */
public interface ReaderUserMessageMapper extends BaseMapper<ReaderUserMessage> {
}
