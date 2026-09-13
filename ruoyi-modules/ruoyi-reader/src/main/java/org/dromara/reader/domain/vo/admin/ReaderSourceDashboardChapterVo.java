package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 小说章节的采集与正文可用性明细。 */
@Data
public class ReaderSourceDashboardChapterVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long chapterId;
    private Long workId;
    private Integer chapterNo;
    private String volumeName;
    private String chapterName;
    private Integer wordCount;
    private String publishStatus;
    private String contentStatus;
    private LocalDateTime updateTime;
}
