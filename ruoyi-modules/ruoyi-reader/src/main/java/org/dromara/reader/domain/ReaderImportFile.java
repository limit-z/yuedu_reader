package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器模块代码，承载 ReaderImportFile 相关业务能力。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_import_file")
public class ReaderImportFile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 导入任务ID。
     */
    private Long taskId;
    /**
     * 原始文件名。
     */
    private String originName;
    /**
     * 文件后缀。
     */
    private String fileSuffix;
    /**
     * OSS 文件ID。
     */
    private Long ossId;
    /**
     * fileSize 字段。
     */
    private Long fileSize;
}
