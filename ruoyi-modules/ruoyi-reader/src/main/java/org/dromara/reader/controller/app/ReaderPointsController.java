package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppPointsVo;
import org.dromara.reader.service.IReaderPointsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 阅读积分控制器。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/points")
public class ReaderPointsController {

    /**
     * 积分服务。
     */
    private final IReaderPointsService pointsService;

    /**
     * 查询积分中心。
     */
    @GetMapping
    public R<AppPointsVo> dashboard() {
        return R.ok(pointsService.getDashboard());
    }

    /**
     * 领取每日签到积分。
     */
    @PostMapping("/checkin")
    public R<AppPointsVo> checkin() {
        return R.ok(pointsService.claimDailyCheckin());
    }

    /**
     * 领取任务积分。
     */
    @PostMapping("/tasks/{taskKey}/claim")
    public R<AppPointsVo> claimTask(@PathVariable String taskKey) {
        return R.ok(pointsService.claimTask(taskKey));
    }
}
