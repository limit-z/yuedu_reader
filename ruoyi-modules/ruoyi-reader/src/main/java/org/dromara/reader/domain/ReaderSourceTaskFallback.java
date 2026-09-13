package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 采集任务的备用书源路由，只有预配置并授权的站点才能被自动使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_task_fallback")
public class ReaderSourceTaskFallback extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    /** 路由主键。 */
    @TableId
    private Long id;
    /** 主任务ID。 */
    private Long taskId;
    /** 优先级，数字越小越优先。 */
    private Integer priority;
    /** 备用站点ID。 */
    private Long siteId;
    /** 备用解析规则ID。 */
    private Long ruleId;
    /** 备用访问策略ID。 */
    private Long policyId;
    /** 单本续采地址模板，支持 {title}、{author}；批量任务可填写列表地址。 */
    private String sourceUrlTemplate;
    /** 是否允许错误后自动生成续采任务。 */
    private String autoEnabled;
    /** 是否启用。 */
    private String status;
    /** 最近一次使用该路由生成的子任务ID。 */
    private Long lastChildTaskId;
}
