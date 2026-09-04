package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 回传的单个章节结果。 */
@Data
public class ReaderSourceWorkerItemBo {
    private String sourceChapterId;
    private String sourceUrl;
    private Integer chapterNo;
    private String chapterName;
    private String content;
    private String contentHash;
    private String titleHash;
}
