package org.dromara.reader.domain.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** H5 积分规则管理视图，反映 ReaderPointsService 当前真实生效的规则。 */
@Data
public class ReaderPointsRuleAdminVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String storage;
    private List<TaskRule> tasks;
    private List<RewardRule> rewards;

    @Data
    @AllArgsConstructor
    public static class TaskRule implements Serializable {
        private String key;
        private String title;
        private Integer points;
        private String condition;
    }

    @Data
    @AllArgsConstructor
    public static class RewardRule implements Serializable {
        private String title;
        private Integer points;
        private String condition;
    }
}
