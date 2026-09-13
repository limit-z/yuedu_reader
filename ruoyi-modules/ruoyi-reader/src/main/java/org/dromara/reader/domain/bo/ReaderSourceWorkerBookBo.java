package org.dromara.reader.domain.bo;

import lombok.Data;

/**
 * Worker 发现的一本来源作品，用于批量任务入队和服务端去重。
 */
@Data
public class ReaderSourceWorkerBookBo {
    /** 来源作品地址。 */
    private String sourceWorkUrl;
    /** 来源作品标题。 */
    private String sourceWorkTitle;
    /** 来源作者。 */
    private String authorName;
    /** 来源内容分类。 */
    private String categoryName;
    /** 来源连载状态：ONGOING 连载中、FINISHED 已完结。 */
    private String serialStatus;
    /** 来源最新章节序号。 */
    private Integer remoteLatestChapterNo;
    /** 来源目录总章节数。 */
    private Integer remoteChapterCount;
}
