package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.bo.ReaderAuditQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderAuditRecordVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.job.ReaderPublishRefreshJob;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.service.IReaderAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 阅读器内容审核服务实现，负责待审记录查询和审核通过后的状态流转。
 */
@RequiredArgsConstructor
@Service
public class ReaderAuditServiceImpl implements IReaderAuditService {

    /**
     * 审核记录访问入口，负责待审列表与审核状态更新。
     */
    private final ReaderContentAuditMapper contentAuditMapper;

    /**
     * 作品主表访问入口，负责审核通过后的发布状态切换。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 小说章节访问入口，负责小说章节批量发布。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 漫画章节访问入口，负责漫画章节批量发布。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 发布刷新任务，负责状态变化后的详情与目录缓存失效。
     */
    private final ReaderPublishRefreshJob publishRefreshJob;
    private final ReaderSourceChapterSnapshotMapper snapshotMapper;
    private final ReaderSourceTaskMapper sourceTaskMapper;
    private final ReaderSourceTaskBookMapper sourceTaskBookMapper;

    /**
     * 审核通过指定内容记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long auditId) {
        ReaderContentAudit audit = contentAuditMapper.selectById(auditId);
        if (audit == null) {
            throw new ServiceException("审核记录不存在");
        }
        // 审核记录先切到 APPROVED，方便后续回溯作品是通过审核发布的。
        audit.setAuditStatus("APPROVED");
        contentAuditMapper.updateById(audit);

        ReaderWork work = readerWorkMapper.selectById(audit.getWorkId());
        if (work == null) {
            throw new ServiceException("作品不存在");
        }
        // 审核通过后作品与章节一起切到已发布状态，避免 C 端出现目录可见但正文不可读。
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        readerWorkMapper.updateById(work);
        novelChapterMapper.update(
            null,
            Wrappers.<ReaderNovelChapter>lambdaUpdate()
                .eq(ReaderNovelChapter::getWorkId, work.getId())
                .set(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
        );
        comicChapterMapper.update(
            null,
            Wrappers.<ReaderComicChapter>lambdaUpdate()
                .eq(ReaderComicChapter::getWorkId, work.getId())
                .set(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
        );
        confirmSourceCollection(audit);
        // 发布状态变化后立即刷新缓存，让首页、详情和阅读页尽快读到最新数据。
        publishRefreshJob.execute(work.getId());
    }

    /** 审核通过后同步清理采集侧待审状态，保证任务明细与审核池状态一致。 */
    private void confirmSourceCollection(ReaderContentAudit audit) {
        if (audit.getSourceTaskBookId() != null) {
            snapshotMapper.update(null, Wrappers.<ReaderSourceChapterSnapshot>lambdaUpdate()
                .eq(ReaderSourceChapterSnapshot::getTaskBookId, audit.getSourceTaskBookId())
                .in(ReaderSourceChapterSnapshot::getSnapshotStatus, List.of("NEW", "CHANGED"))
                .set(ReaderSourceChapterSnapshot::getSnapshotStatus, "CONFIRMED"));
            ReaderSourceTaskBook book = sourceTaskBookMapper.selectById(audit.getSourceTaskBookId());
            if (book != null) {
                book.setStatus("COMPLETED");
                sourceTaskBookMapper.updateById(book);
            }
        } else if (audit.getSourceTaskId() != null) {
            snapshotMapper.update(null, Wrappers.<ReaderSourceChapterSnapshot>lambdaUpdate()
                .eq(ReaderSourceChapterSnapshot::getTaskId, audit.getSourceTaskId())
                .in(ReaderSourceChapterSnapshot::getSnapshotStatus, List.of("NEW", "CHANGED"))
                .set(ReaderSourceChapterSnapshot::getSnapshotStatus, "CONFIRMED"));
        }
        if (audit.getSourceTaskId() != null) {
            ReaderSourceTask task = sourceTaskMapper.selectById(audit.getSourceTaskId());
            if (task != null && snapshotMapper.selectCount(Wrappers.<ReaderSourceChapterSnapshot>lambdaQuery()
                .eq(ReaderSourceChapterSnapshot::getTaskId, task.getId())
                .in(ReaderSourceChapterSnapshot::getSnapshotStatus, List.of("NEW", "CHANGED"))) == 0) {
                task.setStatus("COMPLETED");
                sourceTaskMapper.updateById(task);
            }
        }
    }

    /**
     * 分页查询审核记录列表。
     */
    @Override
    public PageResult<ReaderAuditRecordVo> queryPageList(ReaderAuditQueryBo bo, PageQuery pageQuery) {
        Set<Long> matchedWorkIds = null;
        if (StringUtils.isNotBlank(bo.getWorkTitle())) {
            // 标题筛选需要先找到命中的作品ID，再把审核记录限制在这批作品范围内。
            matchedWorkIds = readerWorkMapper.selectList(
                    Wrappers.<ReaderWork>lambdaQuery().like(ReaderWork::getTitle, bo.getWorkTitle())
                ).stream()
                .map(ReaderWork::getId)
                .collect(Collectors.toSet());
            if (matchedWorkIds.isEmpty()) {
                return PageResult.build(List.of(), 0);
            }
        }

        Page<ReaderContentAudit> page = contentAuditMapper.selectPage(pageQuery.build(), buildQueryWrapper(bo, matchedWorkIds));
        // 清空测试数据后审核列表可能为空，此时不能再继续查作品标题，否则会生成非法的 IN () SQL。
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            return PageResult.build(List.of(), page.getTotal());
        }
        // 审核表本身不存作品标题，这里补一次批量映射给管理端列表展示。
        Map<Long, String> workTitleMap = readerWorkMapper.selectBatchIds(
                page.getRecords().stream().map(ReaderContentAudit::getWorkId).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(ReaderWork::getId, ReaderWork::getTitle, (left, right) -> left));

        List<ReaderAuditRecordVo> rows = page.getRecords().stream()
            .map(audit -> buildAuditRecordVo(audit, workTitleMap))
            .toList();
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 构造审核记录查询条件。
     */
    private LambdaQueryWrapper<ReaderContentAudit> buildQueryWrapper(ReaderAuditQueryBo bo, Set<Long> matchedWorkIds) {
        LambdaQueryWrapper<ReaderContentAudit> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getAuditStatus()), ReaderContentAudit::getAuditStatus, bo.getAuditStatus());
        lqw.in(matchedWorkIds != null, ReaderContentAudit::getWorkId, matchedWorkIds);
        lqw.orderByDesc(ReaderContentAudit::getCreateTime);
        lqw.orderByDesc(ReaderContentAudit::getId);
        return lqw;
    }

    /**
     * 组装审核记录视图对象。
     */
    private ReaderAuditRecordVo buildAuditRecordVo(ReaderContentAudit audit, Map<Long, String> workTitleMap) {
        ReaderAuditRecordVo vo = new ReaderAuditRecordVo();
        vo.setId(audit.getId());
        vo.setWorkId(audit.getWorkId());
        vo.setSourceTaskId(audit.getSourceTaskId());
        vo.setSourceTaskBookId(audit.getSourceTaskBookId());
        vo.setWorkTitle(workTitleMap.get(audit.getWorkId()));
        vo.setAuditStatus(audit.getAuditStatus());
        vo.setAuditComment(audit.getAuditComment());
        vo.setCreateTime(audit.getCreateTime());
        vo.setUpdateTime(audit.getUpdateTime());
        return vo;
    }
}
