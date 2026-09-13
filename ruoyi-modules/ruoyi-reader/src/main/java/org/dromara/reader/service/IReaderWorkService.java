package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.bo.ReaderWorkBo;
import org.dromara.reader.domain.bo.ReaderCoverStyleBo;
import org.dromara.reader.domain.bo.ReaderWorkQueryBo;
import org.dromara.reader.domain.vo.ReaderWorkVo;
import org.dromara.reader.domain.vo.admin.ReaderCatalogAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderComicChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderNovelChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderCoverStyleVo;

import java.util.List;

/**
 * 阅读器模块代码，承载 IReaderWorkService 相关业务能力。
 */
public interface IReaderWorkService {

    /**
     * 创建作品草稿。
     */
    Long createWork(ReaderWorkBo bo);

    /**
     * 按筛选条件分页查询列表数据。
     */
    PageResult<ReaderWorkVo> queryPageList(ReaderWorkQueryBo bo, PageQuery pageQuery);

    /**
     * 查询作品详情。
     */
    ReaderWorkDetailAdminVo queryDetail(Long workId);

    /**
     * 查询作品目录。
     */
    List<ReaderCatalogAdminVo> queryCatalog(Long workId);

    /**
     * 查询小说章节预览。
     */
    ReaderNovelChapterAdminVo queryNovelChapter(Long chapterId);

    /**
     * 查询漫画章节预览。
     */
    ReaderComicChapterAdminVo queryComicChapter(Long chapterId);

    /**
     * 上架作品并同步章节状态。
     */
    void publish(Long workId);

    /**
     * 下架作品并同步章节状态。
     */
    void offline(Long workId);

    /** 为历史作品补齐缺失的自动封面。 */
    int backfillMissingCovers();

    ReaderCoverStyleVo queryGlobalCoverStyle();

    int updateGlobalCoverStyle(ReaderCoverStyleBo bo);

    void updateWorkCoverStyle(Long workId, ReaderCoverStyleBo bo);

    int reformatNovelContents();
}
