package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器专题作品关联实体，负责维护专题下作品集合与排序信息。
 */
@Data
@TableName("reader_topic_work")
@EqualsAndHashCode(callSuper = true)
public class ReaderTopicWork extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 所属专题ID。
     */
    private Long topicId;

    /**
     * 所属作品ID。
     */
    private Long workId;

    /**
     * 专题内排序值。
     */
    private Integer sortNo;

    /**
     * 专题推荐语。
     */
    private String remark;
}
