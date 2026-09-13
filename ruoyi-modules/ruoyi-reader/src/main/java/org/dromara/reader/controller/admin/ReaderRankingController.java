package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderRanking;
import org.dromara.reader.domain.ReaderRankingWork;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.mapper.ReaderRankingMapper;
import org.dromara.reader.mapper.ReaderRankingWorkMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

import java.util.List;

/** 榜单配置管理，支持自动规则榜单与手工编排榜单。 */
@RestController("readerAdminRankingController")
@RequiredArgsConstructor
@RequestMapping("/reader/admin/rankings")
public class ReaderRankingController {

    private final ReaderRankingMapper rankingMapper;
    private final ReaderRankingWorkMapper rankingWorkMapper;
    private final ReaderWorkMapper workMapper;

    @GetMapping("/list")
    @SaCheckPermission("reader:ranking:list")
    public R<PageResult<ReaderRanking>> list(@RequestParam(required = false) String rankingName,
                                             @RequestParam(required = false) String rankingMode,
                                             @RequestParam(required = false) String status,
                                             PageQuery pageQuery) {
        Page<ReaderRanking> page = rankingMapper.selectPage(pageQuery.build(), Wrappers.<ReaderRanking>lambdaQuery()
            .like(StringUtils.isNotBlank(rankingName), ReaderRanking::getRankingName, rankingName)
            .eq(StringUtils.isNotBlank(rankingMode), ReaderRanking::getRankingMode, rankingMode)
            .eq(StringUtils.isNotBlank(status), ReaderRanking::getStatus, status)
            .orderByAsc(ReaderRanking::getSortNo)
            .orderByAsc(ReaderRanking::getId));
        return R.ok(PageResult.build(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/available-works")
    @SaCheckPermission("reader:ranking:list")
    public R<PageResult<ReaderWork>> availableWorks(PageQuery pageQuery) {
        Page<ReaderWork> page = workMapper.selectPage(pageQuery.build(), Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getPublishStatus, "PUBLISHED")
            .orderByDesc(ReaderWork::getUpdateTime)
            .orderByDesc(ReaderWork::getCreateTime));
        return R.ok(PageResult.build(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/{rankingId}/works")
    @SaCheckPermission("reader:ranking:list")
    public R<List<ReaderRankingWork>> works(@PathVariable Long rankingId) {
        return R.ok(rankingWorkMapper.selectList(Wrappers.<ReaderRankingWork>lambdaQuery()
            .eq(ReaderRankingWork::getRankingId, rankingId)
            .orderByAsc(ReaderRankingWork::getSortNo)
            .orderByAsc(ReaderRankingWork::getId)));
    }

    @PostMapping
    @SaCheckPermission("reader:ranking:list")
    public R<Long> add(@RequestBody ReaderRanking ranking) {
        normalize(ranking);
        ranking.setId(null);
        rankingMapper.insert(ranking);
        return R.ok(ranking.getId());
    }

    @PutMapping("/{rankingId}")
    @SaCheckPermission("reader:ranking:list")
    public R<Void> edit(@PathVariable Long rankingId, @RequestBody ReaderRanking ranking) {
        if (rankingMapper.selectById(rankingId) == null) throw new ServiceException("榜单不存在");
        normalize(ranking);
        ranking.setId(rankingId);
        rankingMapper.updateById(ranking);
        return R.ok();
    }

    @PostMapping("/{rankingId}/status/{status}")
    @SaCheckPermission("reader:ranking:list")
    public R<Void> status(@PathVariable Long rankingId, @PathVariable String status) {
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("榜单状态只能为启用或停用");
        ReaderRanking ranking = rankingMapper.selectById(rankingId);
        if (ranking == null) throw new ServiceException("榜单不存在");
        ranking.setStatus(status);
        rankingMapper.updateById(ranking);
        return R.ok();
    }

    /** 批量启用或停用榜单。 */
    @PostMapping("/batch/status/{status}")
    @SaCheckPermission("reader:ranking:list")
    public R<ReaderBatchActionResult> batchStatus(@PathVariable String status, @RequestBody List<Long> ids) {
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("榜单状态只能为启用或停用");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                ReaderRanking ranking = rankingMapper.selectById(id);
                if (ranking == null) return "榜单不存在";
                ranking.setStatus(status);
                rankingMapper.updateById(ranking);
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    @PutMapping("/{rankingId}/works")
    @SaCheckPermission("reader:ranking:list")
    public R<Void> saveWorks(@PathVariable Long rankingId, @RequestBody List<Long> workIds) {
        ReaderRanking ranking = rankingMapper.selectById(rankingId);
        if (ranking == null) throw new ServiceException("榜单不存在");
        rankingWorkMapper.delete(Wrappers.<ReaderRankingWork>lambdaQuery().eq(ReaderRankingWork::getRankingId, rankingId));
        if (workIds == null) return R.ok();
        for (int index = 0; index < workIds.size(); index++) {
            Long workId = workIds.get(index);
            if (workId == null || workMapper.selectById(workId) == null) continue;
            ReaderRankingWork relation = new ReaderRankingWork();
            relation.setRankingId(rankingId);
            relation.setWorkId(workId);
            relation.setSortNo(index + 1);
            relation.setStatus("1");
            rankingWorkMapper.insert(relation);
        }
        return R.ok();
    }

    private void normalize(ReaderRanking ranking) {
        if (ranking == null || StringUtils.isBlank(ranking.getRankingKey()) || StringUtils.isBlank(ranking.getRankingName())) {
            throw new ServiceException("榜单标识和名称不能为空");
        }
        ranking.setRankingKey(ranking.getRankingKey().trim().toLowerCase());
        ranking.setRankingName(ranking.getRankingName().trim());
        ranking.setRankingMode(StringUtils.isBlank(ranking.getRankingMode()) ? "AUTO" : ranking.getRankingMode().toUpperCase());
        ranking.setSortRule(StringUtils.isBlank(ranking.getSortRule()) ? "UPDATE" : ranking.getSortRule().toUpperCase());
        ranking.setSortNo(ranking.getSortNo() == null ? 99 : ranking.getSortNo());
        ranking.setStatus(StringUtils.isBlank(ranking.getStatus()) ? "1" : ranking.getStatus());
    }
}
