package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppPointsVo;

/**
 * 阅读积分服务接口。
 */
public interface IReaderPointsService {

    /**
     * 查询积分中心数据。
     */
    AppPointsVo getDashboard();

    /**
     * 领取每日签到积分。
     */
    AppPointsVo claimDailyCheckin();

    /**
     * 领取某个任务积分。
     */
    AppPointsVo claimTask(String taskKey);
}
