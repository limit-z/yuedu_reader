package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 榜单手工编排的作品顺序。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_ranking_work")
public class ReaderRankingWork extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long rankingId;
    private Long workId;
    private Integer sortNo;
    private String status;
}
