package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 ReaderImportTaskAdminVo 相关业务能力。
 */
@Data
public class ReaderImportTaskAdminVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
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
     * 作品内容分类。
     */
    private String categoryName;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 总处理单元数。
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
    /**
     * 作品封面 OSS 文件ID。
     */
    private Long coverOssId;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
