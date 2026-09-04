package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppReadingStatsVo;

/**
 * 阅读统计服务接口。
 */
public interface IReaderReadingStatsService {

    /**
     * 查询阅读统计。
     */
    AppReadingStatsVo getStats();
}
