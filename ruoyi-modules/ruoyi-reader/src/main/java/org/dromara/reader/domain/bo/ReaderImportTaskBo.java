package org.dromara.reader.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.reader.domain.ReaderImportTask;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器模块代码，承载 ReaderImportTaskBo 相关业务能力。
 */
@Data
@AutoMapper(target = ReaderImportTask.class, reverseConvertGenerate = false)
public class ReaderImportTaskBo implements Serializable {

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
     * 作品内容分类，如玄幻、言情、修仙。
     */
    private String categoryName;
    /**
     * OSS 文件ID。
     */
    private Long ossId;
    /**
     * 作品封面 OSS 文件ID，可选。
     */
    private Long coverOssId;
}
