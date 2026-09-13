package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardChapterVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkDetailVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkVo;

/** 书源采集中心统计与小说维度下钻服务。 */
public interface IReaderSourceDashboardService {

    ReaderSourceDashboardVo overview(int days);

    PageResult<ReaderSourceDashboardWorkVo> queryWorks(String keyword, String categoryName, String status,
                                                        PageQuery pageQuery);

    ReaderSourceDashboardWorkDetailVo getWorkDetail(Long workId);

    PageResult<ReaderSourceDashboardChapterVo> queryWorkChapters(Long workId, String contentStatus,
                                                                   PageQuery pageQuery);
}
