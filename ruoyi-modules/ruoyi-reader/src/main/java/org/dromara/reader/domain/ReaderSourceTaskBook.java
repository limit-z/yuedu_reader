package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 采集任务中的单本书明细，承载去重结果、章节进度和失败信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_task_book")
public class ReaderSourceTaskBook extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细主键。 */
    @TableId
    private Long id;
    /** 所属采集任务。 */
    private Long taskId;
    /** 所属运行批次。 */
    private Long runId;
    /** 展示批次号。 */
    private String batchNo;
    /** 来源作品地址。 */
    private String sourceWorkUrl;
    /** 来源作品标题。 */
    private String sourceWorkTitle;
    /** 来源作者。 */
    private String authorName;
    /** 来源内容分类。 */
    private String categoryName;
    /** 来源连载状态：ONGOING、FINISHED。 */
    private String serialStatus;
    /** 标题加作者的作品去重键。 */
    private String workDedupeKey;
    /** 复用的本地作品ID。 */
    private Long workId;
    /** 去重动作：NEW、REUSE、INCREMENTAL、UNCHANGED。 */
    private String dedupeAction;
    /** 明细状态。 */
    private String status;
    /** 本地最新章节序号。 */
    private Integer localLatestChapterNo;
    /** 来源最新章节序号。 */
    private Integer remoteLatestChapterNo;
    /** 计划采集章节数。 */
    private Integer plannedChapterCount;
    /** 已处理章节数。 */
    private Integer processedChapterCount;
    /** 成功章节数。 */
    private Integer successChapterCount;
    /** 跳过章节数。 */
    private Integer skippedChapterCount;
    /** 失败章节数。 */
    private Integer failureChapterCount;
    /** 明细开始时间。 */
    private LocalDateTime startedAt;
    /** 明细结束时间。 */
    private LocalDateTime finishedAt;
    /** 脱敏后的最后错误。 */
    private String lastError;
}
