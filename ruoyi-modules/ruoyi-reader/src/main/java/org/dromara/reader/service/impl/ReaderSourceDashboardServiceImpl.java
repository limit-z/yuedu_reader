package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderSourceTaskLog;
import org.dromara.reader.domain.ReaderSourceTaskRun;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardChapterVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardErrorVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardLogVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardTaskVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkDetailVo;
import org.dromara.reader.domain.vo.admin.ReaderSourceDashboardWorkVo;
import org.dromara.reader.mapper.ReaderCoverCrawlTaskMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceDiscoveryCandidateMapper;
import org.dromara.reader.mapper.ReaderSourceDiscoveryProviderMapper;
import org.dromara.reader.mapper.ReaderSourceErrorMapper;
import org.dromara.reader.mapper.ReaderSourcePolicyMapper;
import org.dromara.reader.mapper.ReaderSourceRuleMapper;
import org.dromara.reader.mapper.ReaderSourceSiteMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderSourceTaskFallbackMapper;
import org.dromara.reader.mapper.ReaderSourceTaskLogMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderSourceTaskRunMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderSourceDashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * 采集中心统计聚合服务。
 *
 * <p>统计从任务、运行、书籍明细、业务章节、错误和日志表实时计算，避免维护一套容易与采集状态
 * 脱节的冗余计数。正文表只查询章节 ID，不把正文内容加载进大盘请求。</p>
 */
@Service
@RequiredArgsConstructor
public class ReaderSourceDashboardServiceImpl implements IReaderSourceDashboardService {

    private final ReaderSourceSiteMapper siteMapper;
    private final ReaderSourcePolicyMapper policyMapper;
    private final ReaderSourceRuleMapper ruleMapper;
    private final ReaderSourceTaskMapper taskMapper;
    private final ReaderSourceTaskRunMapper runMapper;
    private final ReaderSourceTaskBookMapper taskBookMapper;
    private final ReaderSourceTaskFallbackMapper fallbackMapper;
    private final ReaderSourceTaskLogMapper taskLogMapper;
    private final ReaderSourceErrorMapper errorMapper;
    private final ReaderSourceChapterSnapshotMapper snapshotMapper;
    private final ReaderWorkMapper workMapper;
    private final ReaderNovelChapterMapper chapterMapper;
    private final ReaderNovelChapterContentMapper chapterContentMapper;
    private final ReaderSourceDiscoveryProviderMapper discoveryProviderMapper;
    private final ReaderSourceDiscoveryCandidateMapper discoveryCandidateMapper;
    private final ReaderCoverCrawlTaskMapper coverCrawlTaskMapper;

    @Override
    public ReaderSourceDashboardVo overview(int days) {
        int trendDays = Math.max(1, Math.min(days, 60));
        List<ReaderSourceTask> tasks = taskMapper.selectList(null);
        List<ReaderSourceTaskRun> runs = runMapper.selectList(null);
        List<ReaderSourceTaskBook> books = taskBookMapper.selectList(null);
        List<ReaderSourceError> errors = errorMapper.selectList(null);
        List<ReaderSourceSite> sites = siteMapper.selectList(null);
        List<ReaderSourcePolicy> policies = policyMapper.selectList(null);
        List<ReaderSourceRule> rules = ruleMapper.selectList(null);
        List<ReaderSourceChapterSnapshot> snapshots = List.of();
        List<ReaderNovelChapter> chapters = chapterMapper.selectList(null);
        Map<Long, ReaderSourceTask> taskById = tasks.stream().filter(t -> t.getId() != null)
            .collect(Collectors.toMap(ReaderSourceTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, ReaderSourceSite> siteById = sites.stream().filter(s -> s.getId() != null)
            .collect(Collectors.toMap(ReaderSourceSite::getId, Function.identity(), (a, b) -> a));
        Set<Long> readyChapterIds = contentReadyChapterIds(chapters.stream().map(ReaderNovelChapter::getId)
            .filter(Objects::nonNull).toList());

        ReaderSourceDashboardVo vo = new ReaderSourceDashboardVo();
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setTaskTotal((long) tasks.size());
        vo.setActiveTaskTotal(tasks.stream().filter(t -> "RUNNING".equals(t.getStatus())).count());
        vo.setRunTotal((long) runs.size());
        vo.setRunningRunTotal(runs.stream().filter(r -> "RUNNING".equals(r.getStatus())).count());
        vo.setBookTotal((long) books.size());
        vo.setWorkTotal(workMapper.selectCount(null).longValue());
        vo.setChapterTotal((long) chapters.size());
        vo.setContentReadyChapterTotal((long) readyChapterIds.size());
        vo.setPlannedChapterTotal(sum(books, b -> number(b.getPlannedChapterCount())));
        vo.setProcessedChapterTotal(sum(books, b -> number(b.getProcessedChapterCount())));
        vo.setSuccessChapterTotal(sum(books, b -> number(b.getSuccessChapterCount())));
        vo.setSkippedChapterTotal(sum(books, b -> number(b.getSkippedChapterCount())));
        vo.setFailedChapterTotal(sum(books, b -> number(b.getFailureChapterCount())));
        vo.setPendingChapterTotal(Math.max(0L, vo.getPlannedChapterTotal() - vo.getProcessedChapterTotal()));
        vo.setSnapshotTotal(snapshotMapper.selectCount(null).longValue());
        vo.setErrorTotal((long) errors.size());
        vo.setUnresolvedErrorTotal(errors.stream().filter(e -> !"1".equals(e.getResolved())).count());
        vo.setTaskLogTotal(taskLogMapper.selectCount(null).longValue());
        vo.setEnabledSiteTotal(sites.stream().filter(s -> "1".equals(s.getStatus())).count());
        vo.setApprovedSiteTotal(sites.stream().filter(s -> "APPROVED".equals(s.getComplianceStatus())).count());
        vo.setPolicyTotal((long) policies.size());
        vo.setActivePolicyTotal(policies.stream().filter(p -> "1".equals(p.getStatus())).count());
        vo.setRuleTotal((long) rules.size());
        vo.setActiveRuleTotal(rules.stream().filter(r -> "1".equals(r.getStatus())).count());
        vo.setFallbackTotal(fallbackMapper.selectCount(null).longValue());
        vo.setDiscoveryProviderTotal(discoveryProviderMapper.selectCount(null).longValue());
        vo.setDiscoveryCandidateTotal(discoveryCandidateMapper.selectCount(null).longValue());
        vo.setCoverTaskTotal(coverCrawlTaskMapper.selectCount(null).longValue());
        vo.setCoverCompletedTotal(coverCrawlTaskMapper.selectCount(Wrappers.<org.dromara.reader.domain.ReaderCoverCrawlTask>lambdaQuery()
            .eq(org.dromara.reader.domain.ReaderCoverCrawlTask::getStatus, "COMPLETED")).longValue());
        vo.setCoverRunningTotal(coverCrawlTaskMapper.selectCount(Wrappers.<org.dromara.reader.domain.ReaderCoverCrawlTask>lambdaQuery()
            .eq(org.dromara.reader.domain.ReaderCoverCrawlTask::getStatus, "RUNNING")).longValue());
        vo.setCoverFailedTotal(coverCrawlTaskMapper.selectCount(Wrappers.<org.dromara.reader.domain.ReaderCoverCrawlTask>lambdaQuery()
            .eq(org.dromara.reader.domain.ReaderCoverCrawlTask::getStatus, "FAILED")).longValue());
        vo.setTaskStatusCounts(countBy(tasks, ReaderSourceTask::getStatus));
        vo.setRunStatusCounts(countBy(runs, ReaderSourceTaskRun::getStatus));
        vo.setBookStatusCounts(countBy(books, ReaderSourceTaskBook::getStatus));
        vo.setFailureCodeCounts(countBy(tasks, ReaderSourceTask::getFailureCode));
        vo.setErrorTypeCounts(countBy(errors, ReaderSourceError::getErrorType));
        vo.setExecutorCounts(countBy(runs, ReaderSourceTaskRun::getExecutorType));
        vo.setTriggerCounts(countBy(runs, ReaderSourceTaskRun::getTriggerType));
        vo.setSiteStats(buildSiteStats(sites, tasks, books, runs, taskById));
        vo.setRecentTrend(buildTrend(runs, trendDays));
        vo.setRecentFailures(buildRecentFailureItems(errors));
        return vo;
    }

    @Override
    public PageResult<ReaderSourceDashboardWorkVo> queryWorks(String keyword, String categoryName, String status,
                                                               PageQuery pageQuery) {
        List<ReaderSourceTaskBook> books = taskBookMapper.selectList(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
            .isNotNull(ReaderSourceTaskBook::getWorkId));
        List<ReaderSourceTask> tasks = taskMapper.selectList(null);
        Map<Long, ReaderSourceTask> taskById = tasks.stream().filter(t -> t.getId() != null)
            .collect(Collectors.toMap(ReaderSourceTask::getId, Function.identity(), (a, b) -> a));
        Map<Long, ReaderWork> works = workMapper.selectList(null).stream().filter(w -> w.getId() != null)
            .collect(Collectors.toMap(ReaderWork::getId, Function.identity(), (a, b) -> a));
        List<ReaderNovelChapter> chapters = chapterMapper.selectList(null);
        Map<Long, Long> chapterCounts = chapters.stream().filter(c -> c.getWorkId() != null)
            .collect(Collectors.groupingBy(ReaderNovelChapter::getWorkId, Collectors.counting()));
        Set<Long> readyIds = contentReadyChapterIds(chapters.stream().map(ReaderNovelChapter::getId)
            .filter(Objects::nonNull).toList());
        Map<Long, Long> readyCounts = chapters.stream().filter(c -> c.getWorkId() != null && readyIds.contains(c.getId()))
            .collect(Collectors.groupingBy(ReaderNovelChapter::getWorkId, Collectors.counting()));

        List<ReaderSourceDashboardWorkVo> rows = books.stream().collect(Collectors.groupingBy(ReaderSourceTaskBook::getWorkId))
            .entrySet().stream().map(entry -> buildWorkRow(entry.getKey(), entry.getValue(), taskById,
                works.get(entry.getKey()), chapterCounts, readyCounts))
            .filter(row -> matchesWork(row, keyword, categoryName, status))
            .sorted(Comparator.comparing(ReaderSourceDashboardWorkVo::getLastActivityAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return page(rows, pageQuery);
    }

    @Override
    public ReaderSourceDashboardWorkDetailVo getWorkDetail(Long workId) {
        ReaderWork work = workMapper.selectById(workId);
        if (work == null) {
            throw new ServiceException("作品不存在");
        }
        List<ReaderSourceTaskBook> books = taskBookMapper.selectList(Wrappers.<ReaderSourceTaskBook>lambdaQuery()
            .eq(ReaderSourceTaskBook::getWorkId, workId));
        Set<Long> taskIds = books.stream().map(ReaderSourceTaskBook::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<ReaderSourceTask> tasks = taskIds.isEmpty() ? List.of() : taskMapper.selectBatchIds(taskIds);
        Map<Long, ReaderSourceSite> sites = siteMapper.selectList(null).stream().filter(s -> s.getId() != null)
            .collect(Collectors.toMap(ReaderSourceSite::getId, Function.identity(), (a, b) -> a));
        List<ReaderNovelChapter> chapters = chapterMapper.selectList(Wrappers.<ReaderNovelChapter>lambdaQuery()
            .eq(ReaderNovelChapter::getWorkId, workId).orderByAsc(ReaderNovelChapter::getChapterNo));
        Set<Long> readyIds = contentReadyChapterIds(chapters.stream().map(ReaderNovelChapter::getId)
            .filter(Objects::nonNull).toList());

        ReaderSourceDashboardWorkDetailVo vo = new ReaderSourceDashboardWorkDetailVo();
        vo.setWorkId(workId);
        vo.setTitle(work.getTitle());
        vo.setAuthorName(work.getAuthorName());
        vo.setCategoryName(work.getCategoryName());
        vo.setSerialStatus(work.getSerialStatus());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setTaskCount((long) tasks.size());
        vo.setBookRecordCount((long) books.size());
        vo.setChapterTotal((long) chapters.size());
        vo.setContentReadyChapterCount((long) readyIds.size());
        vo.setContentMissingChapterCount(Math.max(0L, chapters.size() - readyIds.size()));
        vo.setPlannedChapterCount(sum(books, b -> number(b.getPlannedChapterCount())));
        vo.setProcessedChapterCount(sum(books, b -> number(b.getProcessedChapterCount())));
        vo.setSuccessChapterCount(sum(books, b -> number(b.getSuccessChapterCount())));
        vo.setSkippedChapterCount(sum(books, b -> number(b.getSkippedChapterCount())));
        vo.setFailedChapterCount(sum(books, b -> number(b.getFailureChapterCount())));
        vo.setProgressPercent(percent(vo.getProcessedChapterCount(), vo.getPlannedChapterCount()));
        vo.setTaskStatusCounts(countBy(tasks, ReaderSourceTask::getStatus));
        vo.setBookStatusCounts(countBy(books, ReaderSourceTaskBook::getStatus));
        vo.setChapterStatusCounts(chapterStatusCounts(chapters, readyIds));

        List<ReaderSourceDashboardTaskVo> taskRows = tasks.stream()
            .map(task -> buildTaskRow(task, books.stream().filter(b -> Objects.equals(task.getId(), b.getTaskId())).toList(), sites))
            .sorted(Comparator.comparing(ReaderSourceDashboardTaskVo::getCurrent,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(
                ReaderSourceDashboardTaskVo::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        Long currentTaskId = taskRows.stream().filter(task -> Boolean.TRUE.equals(task.getCurrent()))
            .map(ReaderSourceDashboardTaskVo::getTaskId).findFirst().orElse(null);
        if (currentTaskId == null && !taskRows.isEmpty()) currentTaskId = taskRows.get(0).getTaskId();
        vo.setCurrentTaskId(currentTaskId);
        if (currentTaskId != null) {
            Long selectedTaskId = currentTaskId;
            ReaderSourceDashboardTaskVo current = taskRows.stream().filter(t -> selectedTaskId.equals(t.getTaskId())).findFirst().orElse(null);
            if (current != null) {
                vo.setCurrentTaskName(current.getTaskName());
                vo.setCurrentTaskStatus(current.getStatus());
            }
        }
        vo.setTasks(taskRows);

        List<ReaderSourceError> errors = errorMapper.selectList(null).stream()
            .filter(e -> taskIds.contains(e.getTaskId()))
            .sorted(Comparator.comparing(ReaderSourceError::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(50).toList();
        vo.setRecentErrors(errors.stream().map(this::toErrorVo).toList());
        List<ReaderSourceTaskLog> logs = taskLogMapper.selectList(null).stream()
            .filter(l -> taskIds.contains(l.getTaskId()))
            .sorted(Comparator.comparing(ReaderSourceTaskLog::getEventAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(80).toList();
        vo.setRecentLogs(logs.stream().map(this::toLogVo).toList());
        return vo;
    }

    @Override
    public PageResult<ReaderSourceDashboardChapterVo> queryWorkChapters(Long workId, String contentStatus,
                                                                          PageQuery pageQuery) {
        if (workMapper.selectById(workId) == null) {
            throw new ServiceException("作品不存在");
        }
        QueryWrapper<ReaderNovelChapter> wrapper = Wrappers.query();
        wrapper.eq("work_id", workId).orderByAsc("chapter_no").orderByAsc("id");
        if ("READY".equalsIgnoreCase(contentStatus)) {
            wrapper.inSql("id", "select chapter_id from reader_novel_chapter_content where content is not null and content <> ''");
        } else if ("MISSING".equalsIgnoreCase(contentStatus)) {
            wrapper.notInSql("id", "select chapter_id from reader_novel_chapter_content where content is not null and content <> ''");
        }
        Page<ReaderNovelChapter> page = chapterMapper.selectPage(pageQuery.build(), wrapper);
        Set<Long> readyIds = contentReadyChapterIds(page.getRecords().stream().map(ReaderNovelChapter::getId)
            .filter(Objects::nonNull).toList());
        List<ReaderSourceDashboardChapterVo> rows = page.getRecords().stream().map(chapter -> toChapterVo(chapter,
            readyIds.contains(chapter.getId()) ? "READY" : "MISSING")).toList();
        return PageResult.build(rows, page.getTotal());
    }

    private ReaderSourceDashboardWorkVo buildWorkRow(Long workId, List<ReaderSourceTaskBook> books,
                                                       Map<Long, ReaderSourceTask> taskById, ReaderWork work,
                                                       Map<Long, Long> chapterCounts, Map<Long, Long> readyCounts) {
        ReaderSourceDashboardWorkVo row = new ReaderSourceDashboardWorkVo();
        row.setWorkId(workId);
        if (work != null) {
            row.setTitle(work.getTitle());
            row.setAuthorName(work.getAuthorName());
            row.setCategoryName(work.getCategoryName());
            row.setSerialStatus(work.getSerialStatus());
            row.setPublishStatus(work.getPublishStatus());
        } else {
            row.setTitle(books.get(0).getSourceWorkTitle());
            row.setAuthorName(books.get(0).getAuthorName());
            row.setCategoryName(books.get(0).getCategoryName());
        }
        row.setTaskCount(books.stream().map(ReaderSourceTaskBook::getTaskId).filter(Objects::nonNull).distinct().count());
        row.setBookRecordCount((long) books.size());
        row.setPlannedChapterCount(sum(books, b -> number(b.getPlannedChapterCount())));
        row.setProcessedChapterCount(sum(books, b -> number(b.getProcessedChapterCount())));
        row.setSuccessChapterCount(sum(books, b -> number(b.getSuccessChapterCount())));
        row.setSkippedChapterCount(sum(books, b -> number(b.getSkippedChapterCount())));
        row.setFailedChapterCount(sum(books, b -> number(b.getFailureChapterCount())));
        row.setChapterTotal(chapterCounts.getOrDefault(workId, 0L));
        row.setContentReadyChapterCount(readyCounts.getOrDefault(workId, 0L));
        row.setContentMissingChapterCount(Math.max(0L, row.getChapterTotal() - row.getContentReadyChapterCount()));
        row.setProgressPercent(percent(row.getProcessedChapterCount(), row.getPlannedChapterCount()));
        ReaderSourceTask current = selectCurrentTask(books, taskById);
        if (current != null) {
            row.setCurrentTaskId(current.getId());
            row.setCurrentTaskName(current.getTaskName());
            row.setCurrentTaskStatus(current.getStatus());
        }
        row.setLastActivityAt(books.stream().map(ReaderSourceTaskBook::getUpdateTime)
            .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        return row;
    }

    private ReaderSourceDashboardTaskVo buildTaskRow(ReaderSourceTask task, List<ReaderSourceTaskBook> books,
                                                      Map<Long, ReaderSourceSite> sites) {
        ReaderSourceDashboardTaskVo row = new ReaderSourceDashboardTaskVo();
        row.setTaskId(task.getId());
        row.setTaskName(task.getTaskName());
        row.setSiteId(task.getSiteId());
        ReaderSourceSite site = sites.get(task.getSiteId());
        if (site != null) row.setSiteName(site.getSiteName());
        row.setExecutorType(task.getExecutorType());
        row.setStatus(task.getStatus());
        row.setFailureCode(task.getFailureCode());
        row.setFailReason(task.getFailReason());
        row.setBookCount((long) books.size());
        row.setPlannedChapterCount(sum(books, b -> number(b.getPlannedChapterCount())));
        row.setProcessedChapterCount(sum(books, b -> number(b.getProcessedChapterCount())));
        row.setSuccessChapterCount(sum(books, b -> number(b.getSuccessChapterCount())));
        row.setSkippedChapterCount(sum(books, b -> number(b.getSkippedChapterCount())));
        row.setFailedChapterCount(sum(books, b -> number(b.getFailureChapterCount())));
        row.setProgressPercent(percent(row.getProcessedChapterCount(), row.getPlannedChapterCount()));
        row.setCurrent("RUNNING".equals(task.getStatus()) || "PAUSED".equals(task.getStatus()) || "FAILED".equals(task.getStatus()));
        row.setLastRunAt(task.getLastRunAt());
        row.setLastActivityAt(books.stream().map(ReaderSourceTaskBook::getUpdateTime).filter(Objects::nonNull)
            .max(LocalDateTime::compareTo).orElse(task.getUpdateTime()));
        return row;
    }

    private ReaderSourceTask selectCurrentTask(List<ReaderSourceTaskBook> books, Map<Long, ReaderSourceTask> taskById) {
        return books.stream().map(ReaderSourceTaskBook::getTaskId).filter(Objects::nonNull).distinct()
            .map(taskById::get).filter(Objects::nonNull)
            .sorted(Comparator.comparing((ReaderSourceTask task) -> "RUNNING".equals(task.getStatus()), Comparator.reverseOrder())
                .thenComparing(task -> "PAUSED".equals(task.getStatus()), Comparator.reverseOrder())
                .thenComparing(ReaderSourceTask::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
            .findFirst().orElse(null);
    }

    private List<ReaderSourceDashboardVo.SiteStat> buildSiteStats(List<ReaderSourceSite> sites,
                                                                    List<ReaderSourceTask> tasks,
                                                                    List<ReaderSourceTaskBook> books,
                                                                    List<ReaderSourceTaskRun> runs,
                                                                    Map<Long, ReaderSourceTask> taskById) {
        Map<Long, List<ReaderSourceTaskBook>> booksByTask = books.stream().filter(b -> b.getTaskId() != null)
            .collect(Collectors.groupingBy(ReaderSourceTaskBook::getTaskId));
        List<ReaderSourceDashboardVo.SiteStat> result = new ArrayList<>();
        for (ReaderSourceSite site : sites) {
            List<ReaderSourceTask> siteTasks = tasks.stream().filter(t -> Objects.equals(site.getId(), t.getSiteId())).toList();
            Set<Long> taskIds = siteTasks.stream().map(ReaderSourceTask::getId).filter(Objects::nonNull).collect(Collectors.toSet());
            List<ReaderSourceTaskRun> siteRuns = runs.stream().filter(r -> taskIds.contains(r.getTaskId())).toList();
            List<ReaderSourceTaskBook> siteBooks = taskIds.stream().flatMap(id -> booksByTask.getOrDefault(id, List.of()).stream()).toList();
            ReaderSourceDashboardVo.SiteStat stat = new ReaderSourceDashboardVo.SiteStat();
            stat.setSiteId(site.getId());
            stat.setSiteName(site.getSiteName());
            stat.setAllowedHost(site.getAllowedHost());
            stat.setStatus(site.getStatus());
            stat.setComplianceStatus(site.getComplianceStatus());
            stat.setTaskCount((long) siteTasks.size());
            stat.setRunningTaskCount(siteTasks.stream().filter(t -> "RUNNING".equals(t.getStatus())).count());
            stat.setBookCount((long) siteBooks.size());
            stat.setSuccessChapterCount(sum(siteBooks, b -> number(b.getSuccessChapterCount())));
            stat.setFailedChapterCount(sum(siteBooks, b -> number(b.getFailureChapterCount())));
            stat.setRequestCount(sum(siteRuns, r -> number(r.getRequestCount())));
            stat.setRunCount((long) siteRuns.size());
            result.add(stat);
        }
        return result.stream().sorted(Comparator.comparing(ReaderSourceDashboardVo.SiteStat::getBookCount,
            Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ReaderSourceDashboardVo.SiteStat::getSiteId)).toList();
    }

    private List<ReaderSourceDashboardVo.TrendStat> buildTrend(List<ReaderSourceTaskRun> runs, int days) {
        LocalDate end = LocalDate.now();
        Map<LocalDate, ReaderSourceDashboardVo.TrendStat> map = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = end.minusDays(i);
            ReaderSourceDashboardVo.TrendStat stat = new ReaderSourceDashboardVo.TrendStat();
            stat.setDate(date);
            stat.setRunCount(0L);
            stat.setSuccessCount(0L);
            stat.setFailureCount(0L);
            stat.setRequestCount(0L);
            map.put(date, stat);
        }
        for (ReaderSourceTaskRun run : runs) {
            LocalDateTime time = run.getStartedAt() == null ? run.getCreateTime() : run.getStartedAt();
            if (time == null || !map.containsKey(time.toLocalDate())) continue;
            ReaderSourceDashboardVo.TrendStat stat = map.get(time.toLocalDate());
            stat.setRunCount(stat.getRunCount() + 1);
            stat.setSuccessCount(stat.getSuccessCount() + number(run.getSuccessCount()));
            stat.setFailureCount(stat.getFailureCount() + number(run.getFailureCount()));
            stat.setRequestCount(stat.getRequestCount() + number(run.getRequestCount()));
        }
        return new ArrayList<>(map.values());
    }

    private List<ReaderSourceDashboardVo.CountItem> buildRecentFailureItems(List<ReaderSourceError> errors) {
        return errors.stream().filter(e -> !"1".equals(e.getResolved()))
            .sorted(Comparator.comparing(ReaderSourceError::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(8).map(error -> {
                ReaderSourceDashboardVo.CountItem item = new ReaderSourceDashboardVo.CountItem();
                item.setKey(error.getErrorType());
                item.setLabel((error.getHttpStatus() == null ? "" : error.getHttpStatus() + " ") + error.getMessage());
                item.setCount(1L);
                return item;
            }).toList();
    }

    private Map<String, Long> chapterStatusCounts(List<ReaderNovelChapter> chapters, Set<Long> readyIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("READY", chapters.stream().filter(c -> readyIds.contains(c.getId())).count());
        result.put("MISSING", chapters.stream().filter(c -> !readyIds.contains(c.getId())).count());
        result.put("PUBLISHED", chapters.stream().filter(c -> "1".equals(c.getPublishStatus())).count());
        result.put("UNPUBLISHED", chapters.stream().filter(c -> !"1".equals(c.getPublishStatus())).count());
        return result;
    }

    private Set<Long> contentReadyChapterIds(Collection<Long> chapterIds) {
        if (chapterIds.isEmpty()) return Set.of();
        List<Object> ids = chapterContentMapper.selectObjs(new QueryWrapper<ReaderNovelChapterContent>()
            .select("chapter_id").in("chapter_id", chapterIds)
            .isNotNull("content").ne("content", ""));
        return ids.stream().map(this::toLong).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private ReaderSourceDashboardChapterVo toChapterVo(ReaderNovelChapter chapter, String contentStatus) {
        ReaderSourceDashboardChapterVo vo = new ReaderSourceDashboardChapterVo();
        vo.setChapterId(chapter.getId());
        vo.setWorkId(chapter.getWorkId());
        vo.setChapterNo(chapter.getChapterNo());
        vo.setVolumeName(chapter.getVolumeName());
        vo.setChapterName(chapter.getChapterName());
        vo.setWordCount(chapter.getWordCount());
        vo.setPublishStatus(chapter.getPublishStatus());
        vo.setContentStatus(contentStatus);
        vo.setUpdateTime(chapter.getUpdateTime());
        return vo;
    }

    private ReaderSourceDashboardErrorVo toErrorVo(ReaderSourceError error) {
        ReaderSourceDashboardErrorVo vo = new ReaderSourceDashboardErrorVo();
        vo.setId(error.getId());
        vo.setTaskId(error.getTaskId());
        vo.setRunId(error.getRunId());
        vo.setErrorType(error.getErrorType());
        vo.setHttpStatus(error.getHttpStatus());
        vo.setMessage(error.getMessage());
        vo.setResolved(error.getResolved());
        vo.setRetryAt(error.getRetryAt());
        vo.setCreateTime(error.getCreateTime());
        return vo;
    }

    private ReaderSourceDashboardLogVo toLogVo(ReaderSourceTaskLog log) {
        ReaderSourceDashboardLogVo vo = new ReaderSourceDashboardLogVo();
        vo.setId(log.getId());
        vo.setTaskId(log.getTaskId());
        vo.setRunId(log.getRunId());
        vo.setTaskBookId(log.getTaskBookId());
        vo.setLevel(log.getLevel());
        vo.setEventType(log.getEventType());
        vo.setMessage(log.getMessage());
        vo.setDetailJson(log.getDetailJson());
        vo.setEventAt(log.getEventAt());
        return vo;
    }

    private boolean matchesWork(ReaderSourceDashboardWorkVo row, String keyword, String categoryName, String status) {
        boolean keywordMatch = StringUtils.isBlank(keyword)
            || contains(row.getTitle(), keyword) || contains(row.getAuthorName(), keyword)
            || contains(row.getCategoryName(), keyword);
        boolean categoryMatch = StringUtils.isBlank(categoryName) || Objects.equals(row.getCategoryName(), categoryName);
        boolean statusMatch = StringUtils.isBlank(status) || Objects.equals(row.getCurrentTaskStatus(), status);
        return keywordMatch && categoryMatch && statusMatch;
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private <T> PageResult<T> page(List<T> rows, PageQuery pageQuery) {
        int pageNum = pageQuery.getPageNum() == null || pageQuery.getPageNum() < 1 ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery.getPageSize() == null || pageQuery.getPageSize() < 1
            ? Integer.MAX_VALUE : pageQuery.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return PageResult.build(rows.subList(from, to), (long) rows.size());
    }

    private <T> Map<String, Long> countBy(Collection<T> items, Function<T, String> extractor) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (T item : items) {
            String key = extractor.apply(item);
            if (StringUtils.isBlank(key)) continue;
            result.put(key, result.getOrDefault(key, 0L) + 1);
        }
        return result;
    }

    private <T> long sum(Collection<T> items, ToLongFunction<T> extractor) {
        return items.stream().mapToLong(extractor).sum();
    }

    private long number(Integer value) {
        return value == null ? 0L : value;
    }

    private Integer percent(long processed, long planned) {
        if (planned <= 0) return 0;
        return (int) Math.min(100L, Math.round(processed * 100D / planned));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
