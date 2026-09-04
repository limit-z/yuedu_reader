package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.domain.vo.app.AppReadingProgressVo;

/**
 * 阅读器模块代码，承载 IReaderProgressService 相关业务能力。
 */
public interface IReaderProgressService {

    /**
     * 保存阅读进度。
     */
    void saveProgress(ReaderProgressBo bo);

    /**
     * 获取作品阅读进度。
     */
    AppReadingProgressVo getProgress(Long workId);
}
