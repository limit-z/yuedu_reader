package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderImportTask 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_import_task")
public class ReaderImportTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 导入任务名称。
     */
    private String taskName;
    /**
     * 内容类型。
     */
    private String contentType;
    /**
     * 作品内容分类，如玄幻、言情、修仙。
     */
    private String categoryName;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * OSS 文件ID。
     */
    private Long ossId;
    /**
     * 作品封面 OSS 文件ID，可选，未提供时由客户端使用默认封面。
     */
    private Long coverOssId;
    /**
     * 总处理单元数，小说场景下表示章节数，漫画场景下表示页数。
     */
    private Integer totalUnits;
    /**
     * 已完成处理单元数。
     */
    private Integer processedUnits;
    /**
     * 当前进度百分比。
     */
    private Integer progressPercent;
    /**
     * 当前进度说明。
     */
    private String progressMessage;
    /**
     * 失败原因。
     */
    private String failReason;
}
