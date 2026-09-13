package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;
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
    /**
     * 采集模式：SINGLE 单本、ALL 全站、CATEGORY 按分类。
     */
    private String collectionMode;
    /** 批量采集最多处理的书籍数量；单本任务固定为 1。 */
    private Integer bookLimit;
    /**
     * 按分类采集时使用的来源分类名称。
     */
    private String categoryName;
    /**
     * 采集批次号，便于任务重跑和审计追踪。
     */
    private String batchNo;
    /**
     * 当前批次发现的书籍总数。
     */
    private Integer totalBooks;
    /**
     * 当前批次已处理的书籍数。
     */
    private Integer processedBooks;
    /**
     * 当前批次成功处理的书籍数。
     */
    private Integer successBooks;
    /**
     * 当前批次跳过的书籍数。
     */
    private Integer skippedBooks;
    /**
     * 当前批次失败的书籍数。
     */
    private Integer failedBooks;
    /**
     * 当前批次整体进度百分比。
     */
    private Integer progressPercent;
    /** 是否允许每日额度恢复后的自动重试：1允许、0关闭。 */
    private String dailyRetryEnabled;
    /** 因每日额度耗尽触发的累计自动重试次数。 */
    private Integer dailyRetryCount;
    /** 最近一次每日额度自动重试对应的日期。 */
    private LocalDate lastDailyRetryDate;
    /** 最近一次每日额度自动重试时间。 */
    private LocalDateTime lastDailyRetryAt;
    private String status;
    private Integer currentChapterNo;
    private Integer plannedChapterCount;
    private LocalDateTime lastRunAt;
    /** 最近一次失败分类：DAILY_LIMIT、HTTP_403、TIMEOUT、PARSE、QUALITY 等。 */
    private String failureCode;
    /** 是否启用普通异常自动重试。 */
    private String autoRetryEnabled;
    /** 普通异常自动重试累计次数。 */
    private Integer autoRetryCount;
    /** 普通异常自动重试最大次数。 */
    private Integer maxAutoRetryCount;
    /** 下一次自动重试时间。 */
    private LocalDateTime retryAfter;
    /** 父任务ID；备用书源续采任务通过此字段关联原任务。 */
    private Long parentTaskId;
    /** 创建该续采任务使用的备用书源路由ID。 */
    private Long fallbackId;
    /** 被续采的原任务书籍明细ID。 */
    private Long fallbackSourceTaskBookId;
    private String failReason;

    /** 最新运行记录的展示字段，不落库，用于区分排队与实际执行。 */
    @TableField(exist = false)
    private Long latestRunId;
    @TableField(exist = false)
    private String latestRunStatus;
    @TableField(exist = false)
    private LocalDateTime latestRunClaimedAt;
    /** WAITING_WORKER、COLLECTING、WAITING_DAILY_LIMIT、WAITING_MANUAL 等。 */
    @TableField(exist = false)
    private String executionState;
}
