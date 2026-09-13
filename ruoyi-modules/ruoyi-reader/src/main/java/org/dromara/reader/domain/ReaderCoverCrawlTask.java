package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 持久化的异步封面采集任务。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_cover_crawl_task")
public class ReaderCoverCrawlTask extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long workId;
    private Long sourceTaskId;
    private Long sourceTaskBookId;
    private String status;
    private Integer portraitTargetCount;
    private Integer landscapeTargetCount;
    private Integer portraitSuccessCount;
    private Integer landscapeSuccessCount;
    private String currentProvider;
    private Integer progressPercent;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
