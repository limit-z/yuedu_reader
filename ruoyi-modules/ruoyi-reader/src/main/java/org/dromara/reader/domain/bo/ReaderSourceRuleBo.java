package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 声明式解析规则新增或修改参数。 */
@Data
public class ReaderSourceRuleBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long siteId;
    private String ruleName;
    private String searchUrlTemplate;
    private String detailUrlTemplate;
    private String catalogUrlTemplate;
    private String chapterUrlTemplate;
    private String selectorJson;
    private String testUrl;
    private String remark;
}
