package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderReadingCommentSubmitBo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppReadingCommentVo;

/**
 * 阅读点评服务接口。
 */
public interface IReaderReadingCommentService {

    /**
     * 查询阅读点评列表。
     */
    AppPageVo<AppReadingCommentVo> listComments(Long workId, Long chapterId, Integer pageNum, Integer pageSize);

    /**
     * 提交阅读点评。
     */
    Long submitComment(ReaderReadingCommentSubmitBo bo);
}
