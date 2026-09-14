package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.admin.ReaderPointsRuleAdminVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H5 积分规则管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/h5/points")
public class ReaderPointsAdminController {
    @GetMapping("/rules")
    @SaCheckPermission("reader:h5-points:list")
    public R<ReaderPointsRuleAdminVo> rules() {
        ReaderPointsRuleAdminVo vo = new ReaderPointsRuleAdminVo();
        vo.setStorage("Redis：reader:points:state:{accountType}:{readerId}，当前未建立积分流水表");
        vo.setTasks(List.of(
            new ReaderPointsRuleAdminVo.TaskRule("read", "阅读20分钟", 10, "连续阅读满20分钟"),
            new ReaderPointsRuleAdminVo.TaskRule("review", "发表书评", 15, "提交20字以上真实评价"),
            new ReaderPointsRuleAdminVo.TaskRule("share", "分享好书", 8, "分享给好友或收藏到书架"),
            new ReaderPointsRuleAdminVo.TaskRule("listen", "听书30分钟", 12, "连续播放30分钟")));
        vo.setRewards(List.of(
            new ReaderPointsRuleAdminVo.RewardRule("连续阅读徽章", 180, "累计解锁成就"),
            new ReaderPointsRuleAdminVo.RewardRule("护眼背景", 260, "累计解锁成就"),
            new ReaderPointsRuleAdminVo.RewardRule("阅读称号", 420, "累计解锁成就")));
        return R.ok(vo);
    }
}
