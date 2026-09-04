package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阅读器专题实体，负责承接书城专题运营位与题材推荐集合配置。
 */
@Data
@TableName("reader_topic")
@EqualsAndHashCode(callSuper = true)
public class ReaderTopic extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 专题主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 专题唯一标识。
     */
    private String topicKey;

    /**
     * 专题名称。
     */
    private String topicName;

    /**
     * 专题描述。
     */
    private String topicDesc;

    /**
     * 专题封面地址。
     */
    private String coverUrl;

    /**
     * 专题角标文案。
     */
    private String badgeText;

    /**
     * 作品类型。
     */
    private String workType;

    /**
     * 查看更多路径。
     */
    private String morePath;

    /**
     * 是否在首页展示：1展示、0不展示。
     */
    private String showHome;

    /**
     * 排序值。
     */
    private Integer sortNo;

    /**
     * 状态：1启用、0停用。
     */
    private String status;

    /**
     * 生效开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 生效结束时间。
     */
    private LocalDateTime endTime;
}
