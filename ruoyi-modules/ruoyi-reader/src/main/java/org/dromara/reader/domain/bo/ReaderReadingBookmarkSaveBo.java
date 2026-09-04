package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 阅读器书签保存业务对象。
 */
@Data
public class ReaderReadingBookmarkSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作品ID。
     */
    private Long workId;

    /**
     * 章节ID。
     */
    private Long chapterId;

    /**
     * 阅读定位值。
     */
    private String locationValue;
}
