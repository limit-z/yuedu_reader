package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读器模块代码，承载 AppHomeNoticeVo 相关业务能力。
 */
@Data
public class AppHomeNoticeVo {

    /**
     * 公告ID。
     */
    private Long noticeId;
    /**
     * 标题。
     */
    private String title;
    /**
     * 正文内容。
     */
    private String content;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
