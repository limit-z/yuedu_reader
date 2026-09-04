package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 书源采集任务及其断点游标。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_task")
public class ReaderSourceTask extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String taskName;
    private Long siteId;
    private Long ruleId;
    private Long policyId;
    private String executorType;
    private String sourceWorkUrl;
    private String sourceWorkTitle;
    private Integer startChapterNo;
    private Integer endChapterNo;
    private String incremental;
    private String status;
    private Integer currentChapterNo;
    private Integer plannedChapterCount;
    private LocalDateTime lastRunAt;
    private String failReason;
}
