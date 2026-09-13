package org.dromara.reader.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.ReaderRanking;
import org.dromara.reader.domain.ReaderRankingWork;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppRankingVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderRankingMapper;
import org.dromara.reader.mapper.ReaderRankingWorkMapper;
import org.dromara.reader.service.IReaderRankingService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 排行榜服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReaderRankingServiceImpl implements IReaderRankingService {

    /**
     * 作品访问入口。
     */
    private final ReaderWorkMapper readerWorkMapper;

    private final ReaderRankingMapper readerRankingMapper;

    private final ReaderRankingWorkMapper readerRankingWorkMapper;

    /**
     * 查询榜单列表。
     */
    @Override
    public List<AppRankingVo> listRankings() {
        List<ReaderRanking> configured = loadEnabledRankings();
        if (configured.isEmpty()) {
            return List.of(
                ranking("hot", "畅销榜", "最近被读者频繁打开的热门作品", "AUTO", "HOT", 1),
                ranking("rising", "飙升榜", "近阶段热度上涨最快的作品", "AUTO", "RISING", 2),
                ranking("completed", "完结榜", "已经完结且口碑稳定的作品", "AUTO", "COMPLETED", 3),
                ranking("new", "新书榜", "最近新上架的作品", "AUTO", "NEW", 4)
            );
        }
        return configured.stream().map(this::toRankingVo).toList();
    }

    /**
     * 查询榜单作品。
     */
    @Override
    public AppPageVo<AppWorkCardVo> listRankingWorks(String rankingKey, Integer pageNum, Integer pageSize) {
        ReaderRanking configured = loadEnabledRankings().stream()
            .filter(item -> item.getRankingKey().equalsIgnoreCase(rankingKey))
            .findFirst().orElse(null);
        List<ReaderWork> works = configured != null && "MANUAL".equalsIgnoreCase(configured.getRankingMode())
            ? loadManualWorks(configured)
            : loadAutoWorks(configured == null ? rankingKey : configured.getSortRule());
        List<AppWorkCardVo> cards = works.stream().map(this::toCard).toList();
        return toPage(cards, pageNum, pageSize);
    }

    /**
     * 构造榜单配置。
     */
    private AppRankingVo ranking(String key, String name, String desc, String mode, String sortRule, int sortNo) {
        AppRankingVo vo = new AppRankingVo();
        vo.setRankingKey(key);
        vo.setRankingName(name);
        vo.setRankingDesc(desc);
        vo.setRankingMode(mode);
        vo.setSortRule(sortRule);
        vo.setSortNo(sortNo);
        return vo;
    }

    private AppRankingVo toRankingVo(ReaderRanking ranking) {
        return ranking(ranking.getRankingKey(), ranking.getRankingName(), ranking.getRankingDesc(),
            ranking.getRankingMode(), ranking.getSortRule(), ranking.getSortNo() == null ? 0 : ranking.getSortNo());
    }

    private List<ReaderRanking> loadEnabledRankings() {
        return readerRankingMapper.selectList(Wrappers.<ReaderRanking>lambdaQuery()
            .eq(ReaderRanking::getStatus, "1")
            .orderByAsc(ReaderRanking::getSortNo)
            .orderByAsc(ReaderRanking::getId));
    }

    private List<ReaderWork> loadManualWorks(ReaderRanking ranking) {
        List<ReaderRankingWork> relations = readerRankingWorkMapper.selectList(Wrappers.<ReaderRankingWork>lambdaQuery()
            .eq(ReaderRankingWork::getRankingId, ranking.getId())
            .eq(ReaderRankingWork::getStatus, "1")
            .orderByAsc(ReaderRankingWork::getSortNo)
            .orderByAsc(ReaderRankingWork::getId));
        Map<Long, ReaderWork> workMap = loadPublishedWorks().stream()
            .collect(java.util.stream.Collectors.toMap(ReaderWork::getId, item -> item, (left, right) -> left));
        return relations.stream().map(item -> workMap.get(item.getWorkId())).filter(java.util.Objects::nonNull).toList();
    }

    private List<ReaderWork> loadAutoWorks(String sortRule) {
        List<ReaderWork> works = loadPublishedWorks();
        if ("COMPLETED".equalsIgnoreCase(sortRule)) {
            works = works.stream().filter(this::isCompleted).toList();
        }
        return works.stream().sorted(resolveComparator(sortRule)).toList();
    }

    /**
     * 读取已发布作品。
     */
    private List<ReaderWork> loadPublishedWorks() {
        return readerWorkMapper.selectList(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name())
            .orderByDesc(ReaderWork::getUpdateTime)
            .orderByDesc(ReaderWork::getCreateTime));
    }

    /**
     * 按榜单类型选择排序器。
     */
    private Comparator<ReaderWork> resolveComparator(String rankingKey) {
        if ("new".equalsIgnoreCase(rankingKey)) {
            return Comparator
                .comparing(ReaderWork::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReaderWork::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("completed".equalsIgnoreCase(rankingKey)) {
            return Comparator
                .comparing((ReaderWork work) -> isCompleted(work) ? 0 : 1)
                .thenComparing(ReaderWork::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReaderWork::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("rising".equalsIgnoreCase(rankingKey)) {
            return Comparator
                .comparing(ReaderWork::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReaderWork::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("hot".equalsIgnoreCase(rankingKey)) {
            return Comparator
                .comparing(ReaderWork::getTotalChapters, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReaderWork::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReaderWork::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator
            .comparing(ReaderWork::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ReaderWork::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /**
     * 判断作品是否完结。
     */
    private boolean isCompleted(ReaderWork work) {
        if (work == null || StrUtil.isBlank(work.getSerialStatus())) {
            return false;
        }
        String status = work.getSerialStatus().toUpperCase();
        return status.contains("完结") || status.contains("COMPLETED") || status.contains("FINISHED") || status.contains("END");
    }

    /**
     * 转换为作品卡片。
     */
    private AppWorkCardVo toCard(ReaderWork work) {
        AppWorkCardVo vo = new AppWorkCardVo();
        vo.setWorkId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setAuthorName(work.getAuthorName());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setCoverLandscapeUrl(work.getCoverLandscapeUrl());
        vo.setIntro(work.getIntro());
        vo.setWorkType(work.getWorkType());
        vo.setCategoryName(work.getCategoryName());
        vo.setSerialStatus(work.getSerialStatus());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setTotalChapters(work.getTotalChapters());
        vo.setTotalPages(work.getTotalPages());
        return vo;
    }

    /**
     * 分页结果。
     */
    private AppPageVo<AppWorkCardVo> toPage(List<AppWorkCardVo> list, Integer pageNum, Integer pageSize) {
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int fromIndex = Math.min((currentPage - 1) * currentSize, list.size());
        int toIndex = Math.min(fromIndex + currentSize, list.size());
        AppPageVo<AppWorkCardVo> vo = new AppPageVo<>();
        vo.setList(list.subList(fromIndex, toIndex));
        vo.setTotal((long) list.size());
        vo.setPageNum(currentPage);
        vo.setPageSize(currentSize);
        vo.setTotalPages((int) Math.ceil((double) list.size() / currentSize));
        return vo;
    }
}
