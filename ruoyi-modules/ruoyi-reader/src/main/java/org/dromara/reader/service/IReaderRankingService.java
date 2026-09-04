package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppRankingVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;

import java.util.List;

/**
 * 阅读排行榜服务接口。
 */
public interface IReaderRankingService {

    /**
     * 查询榜单列表。
     */
    List<AppRankingVo> listRankings();

    /**
     * 查询榜单作品。
     */
    AppPageVo<AppWorkCardVo> listRankingWorks(String rankingKey, Integer pageNum, Integer pageSize);
}
