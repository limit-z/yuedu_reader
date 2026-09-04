package org.dromara.reader.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppRankingVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderRankingService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

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

    /**
     * 查询榜单列表。
     */
    @Override
    public List<AppRankingVo> listRankings() {
        return List.of(
            ranking("hot", "畅销榜", "最近被读者频繁打开的热门作品"),
            ranking("rising", "飙升榜", "近阶段热度上涨最快的作品"),
            ranking("completed", "完结榜", "已经完结且口碑稳定的作品"),
            ranking("new", "新书榜", "最近新上架的作品")
        );
    }

    /**
     * 查询榜单作品。
     */
    @Override
    public AppPageVo<AppWorkCardVo> listRankingWorks(String rankingKey, Integer pageNum, Integer pageSize) {
        List<AppWorkCardVo> cards = loadPublishedWorks().stream()
            .sorted(resolveComparator(rankingKey))
            .map(this::toCard)
            .toList();
        return toPage(cards, pageNum, pageSize);
    }

    /**
     * 构造榜单配置。
     */
    private AppRankingVo ranking(String key, String name, String desc) {
        AppRankingVo vo = new AppRankingVo();
        vo.setRankingKey(key);
        vo.setRankingName(name);
        vo.setRankingDesc(desc);
        return vo;
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
        vo.setCoverUrl(work.getCoverUrl());
        vo.setIntro(work.getIntro());
        vo.setWorkType(work.getWorkType());
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
