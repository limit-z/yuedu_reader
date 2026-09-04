package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 书源声明式解析规则，selectorJson 不执行任意脚本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_source_rule")
public class ReaderSourceRule extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long siteId;
    private String ruleName;
    private Integer versionNo;
    private String status;
    private String searchUrlTemplate;
    private String detailUrlTemplate;
    private String catalogUrlTemplate;
    private String chapterUrlTemplate;
    private String selectorJson;
    private String testUrl;
    private String remark;
}
