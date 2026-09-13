package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 采集任务事件日志，记录自动调度、Worker 协议和异常决策。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_task_log")
public class ReaderSourceTaskLog extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    /** 日志主键。 */
    @TableId
    private Long id;
    /** 所属采集任务。 */
    private Long taskId;
    /** 所属运行记录，可为空。 */
    private Long runId;
    /** 所属任务书籍明细，可为空。 */
    private Long taskBookId;
    /** 日志级别：INFO、WARN、ERROR。 */
    private String level;
    /** 阶段：TASK、DISCOVERY、CLAIM、PERMIT、FETCH、RESULT、RETRY、FALLBACK。 */
    private String eventType;
    /** 面向管理端的脱敏日志消息。 */
    private String message;
    /** 结构化扩展信息 JSON。 */
    private String detailJson;
    /** 事件发生时间。 */
    private LocalDateTime eventAt;
}
