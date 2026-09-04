package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderReadingBookmarkSaveBo;
import org.dromara.reader.domain.vo.app.AppReadingBookmarkVo;

import java.util.List;

/**
 * 阅读书签服务接口。
 */
public interface IReaderReadingBookmarkService {

    /**
     * 查询书签列表。
     */
    List<AppReadingBookmarkVo> listBookmarks(Long workId);

    /**
     * 新增书签。
     */
    Long saveBookmark(ReaderReadingBookmarkSaveBo bo);

    /**
     * 删除书签。
     */
    void removeBookmark(Long bookmarkId);
}
