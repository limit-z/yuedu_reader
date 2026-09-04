package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.List;

/**
 * 阅读器专题详情视图对象，负责输出专题头图、描述和作品列表。
 */
@Data
public class AppTopicDetailVo {

    /**
     * 专题唯一标识。
     */
    private String topicKey;
    /**
     * 专题名称。
     */
    private String topicName;
    /**
     * 专题描述。
     */
    private String topicDesc;
    /**
     * 专题封面地址。
     */
    private String coverUrl;
    /**
     * 专题角标文案。
     */
    private String badgeText;
    /**
     * 作品类型。
     */
    private String workType;
    /**
     * 专题作品列表。
     */
    private List<AppWorkCardVo> items;
}
