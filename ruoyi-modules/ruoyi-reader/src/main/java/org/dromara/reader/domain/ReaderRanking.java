package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 阅读器榜单配置，支持系统自动排序和运营手工编排。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_ranking")
public class ReaderRanking extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    /** APP 路由使用的稳定标识。 */
    private String rankingKey;
    private String rankingName;
    private String rankingDesc;
    /** AUTO 自动榜，MANUAL 手工榜。 */
    private String rankingMode;
    /** HOT、RISING、COMPLETED、NEW、UPDATE。 */
    private String sortRule;
    private Integer sortNo;
    /** 1启用、0停用。 */
    private String status;
}
