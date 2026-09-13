package org.dromara.reader.domain.bo;

import lombok.Data;

/** Worker 回传的单个章节结果。 */
@Data
public class ReaderSourceWorkerItemBo {
    /** 批量任务对应的任务书籍明细ID，单本旧任务可以为空。 */
    private Long taskBookId;
    private String sourceChapterId;
    private String sourceUrl;
    private Integer chapterNo;
    private String chapterName;
    private String content;
    private String contentHash;
    private String titleHash;
    /** 作品页解析出的作者，用于修正榜单页缺失的元数据。 */
    private String authorName;
    /** 作品页解析出的分类。 */
    private String categoryName;
    /** 作品页解析出的连载状态：ONGOING、FINISHED。 */
    private String serialStatus;
}
