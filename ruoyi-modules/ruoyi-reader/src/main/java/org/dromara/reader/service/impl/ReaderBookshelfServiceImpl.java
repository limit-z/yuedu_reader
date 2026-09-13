package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderBookshelf;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderReadingHistory;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.vo.app.AppBookshelfItemVo;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderBookshelfService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 阅读器书架服务实现，负责书架增删查和应用侧书架展示。
 */
@RequiredArgsConstructor
@Service
public class ReaderBookshelfServiceImpl implements IReaderBookshelfService {

    private static final JsonMapper READER_LOCATION_MAPPER = JsonMapper.builder().build();

    /**
     * 作品不存在时统一复用的提示语。
     */
    private static final String WORK_NOT_FOUND_MESSAGE = "作品不存在";

    /**
     * 书架访问入口，负责用户书架记录增删查。
     */
    private final ReaderBookshelfMapper bookshelfMapper;

    /**
     * 作品主表访问入口，负责校验作品是否存在且已发布。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 阅读进度访问入口，负责组装书架上的继续阅读信息。
     */
    private final ReaderReadingProgressMapper readingProgressMapper;

    /**
     * 阅读历史访问入口，负责最近阅读列表维护。
     */
    private final ReaderReadingHistoryMapper readingHistoryMapper;

    /**
     * 小说章节访问入口，负责解析最近阅读章节标题。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 漫画章节访问入口，负责解析漫画最近阅读章节标题。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 访客账户服务入口，负责把登录用户与游客态统一为同一套读者主体。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 将作品加入当前用户书架。
     */
    @Override
    public void add(Long workId) {
        Long userId = requireCurrentUserId();
        requirePublishedWork(workId);
        // 书架加入操作先做幂等判断，避免重复点击时产生重复书架记录。
        ReaderBookshelf existing = bookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .eq(ReaderBookshelf::getWorkId, workId));
        if (existing != null) {
            return;
        }
        ReaderBookshelf bookshelf = new ReaderBookshelf();
        bookshelf.setUserId(userId);
        bookshelf.setWorkId(workId);
        bookshelf.setTopPin("0");
        bookshelf.setSortNo(nextBookshelfSortNo(userId));
        bookshelfMapper.insert(bookshelf);
    }

    /**
     * 将作品移出当前用户书架。
     */
    @Override
    public void remove(Long workId) {
        Long userId = requireCurrentUserId();
        bookshelfMapper.delete(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .eq(ReaderBookshelf::getWorkId, workId));
    }

    /**
     * 查询当前用户书架列表。
     */
    @Override
    public List<AppBookshelfItemVo> listBookshelf(String sortBy) {
        Long userId = requireCurrentUserId();
        List<ReaderBookshelf> bookshelves = bookshelfMapper.selectList(buildBookshelfQuery(userId, sortBy));
        if (bookshelves.isEmpty()) {
            return List.of();
        }
        List<Long> workIds = bookshelves.stream().map(ReaderBookshelf::getWorkId).distinct().toList();
        // 书架页只保留已发布作品，避免下架内容继续暴露在 C 端列表里。
        Map<Long, ReaderWork> workMap = readerWorkMapper.selectBatchIds(workIds).stream()
            .filter(work -> PublishStatus.PUBLISHED.name().equals(work.getPublishStatus()))
            .collect(Collectors.toMap(ReaderWork::getId, Function.identity()));
        // 同一本作品可能存在多端同步进度，先折叠成最新进度再组装书架卡片。
        Map<Long, ReaderReadingProgress> progressMap = readingProgressMapper.selectList(Wrappers.<ReaderReadingProgress>lambdaQuery()
                .eq(ReaderReadingProgress::getUserId, userId)
                .in(ReaderReadingProgress::getWorkId, workIds))
            .stream()
            .collect(Collectors.toMap(ReaderReadingProgress::getWorkId, Function.identity(), this::pickLatestProgress));

        return bookshelves.stream()
            .map(bookshelf -> toBookshelfItemVo(bookshelf, workMap.get(bookshelf.getWorkId()), progressMap.get(bookshelf.getWorkId())))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 批量移出书架。
     */
    @Override
    public void batchRemove(List<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return;
        }
        Long userId = requireCurrentUserId();
        bookshelfMapper.delete(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .in(ReaderBookshelf::getWorkId, workIds));
    }

    /**
     * 设置作品在书架中的置顶状态。
     */
    @Override
    public void setTopPin(Long workId, boolean pinned) {
        Long userId = requireCurrentUserId();
        ReaderBookshelf bookshelf = requireBookshelf(userId, workId);
        bookshelf.setTopPin(pinned ? "1" : "0");
        bookshelfMapper.updateById(bookshelf);
    }

    /**
     * 按前端传入顺序重排书架。
     */
    @Override
    public void reorder(List<Long> workIds) {
        Long userId = requireCurrentUserId();
        List<ReaderBookshelf> bookshelves = bookshelfMapper.selectList(buildBookshelfQuery(userId, null));
        if (bookshelves.isEmpty()) {
            return;
        }

        Map<Long, ReaderBookshelf> shelfMap = bookshelves.stream()
            .collect(Collectors.toMap(ReaderBookshelf::getWorkId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<ReaderBookshelf> ordered = new java.util.ArrayList<>();
        if (workIds != null) {
            for (Long workId : workIds) {
                ReaderBookshelf shelf = shelfMap.remove(workId);
                if (shelf != null) {
                    ordered.add(shelf);
                }
            }
        }
        ordered.addAll(shelfMap.values());

        int sortNo = 1;
        for (ReaderBookshelf shelf : ordered) {
            if (!Objects.equals(shelf.getSortNo(), sortNo)) {
                shelf.setSortNo(sortNo);
                bookshelfMapper.updateById(shelf);
            }
            sortNo++;
        }
    }

    /**
     * 查询当前用户阅读历史。
     */
    @Override
    public List<AppHistoryItemVo> listHistory() {
        Long userId = requireCurrentUserId();
        List<ReaderReadingHistory> histories = readingHistoryMapper.selectList(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, userId)
            .orderByDesc(ReaderReadingHistory::getReadAt, ReaderReadingHistory::getUpdateTime));
        if (histories.isEmpty()) {
            return List.of();
        }
        List<Long> workIds = histories.stream().map(ReaderReadingHistory::getWorkId).distinct().toList();
        Map<Long, ReaderWork> workMap = readerWorkMapper.selectBatchIds(workIds).stream()
            .filter(work -> PublishStatus.PUBLISHED.name().equals(work.getPublishStatus()))
            .collect(Collectors.toMap(ReaderWork::getId, Function.identity()));
        return histories.stream()
            .map(history -> toHistoryItemVo(history, workMap.get(history.getWorkId())))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 删除单条阅读历史。
     */
    @Override
    public void removeHistory(Long historyId) {
        Long userId = requireCurrentUserId();
        readingHistoryMapper.delete(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, userId)
            .eq(ReaderReadingHistory::getId, historyId));
    }

    /**
     * 清空当前用户阅读历史。
     */
    @Override
    public void clearHistory() {
        Long userId = requireCurrentUserId();
        readingHistoryMapper.delete(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, userId));
    }

    /**
     * 组装书架项视图对象。
     */
    private AppBookshelfItemVo toBookshelfItemVo(ReaderBookshelf bookshelf, ReaderWork work, ReaderReadingProgress progress) {
        if (work == null) {
            return null;
        }
        AppBookshelfItemVo vo = new AppBookshelfItemVo();
        vo.setWorkId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setCoverLandscapeUrl(work.getCoverLandscapeUrl());
        vo.setWorkType(work.getWorkType());
        vo.setTopPin("1".equals(bookshelf.getTopPin()));
        vo.setSortNo(bookshelf.getSortNo());
        vo.setUpdatedAt(progress != null && progress.getProgressUpdatedAt() != null ? progress.getProgressUpdatedAt() : bookshelf.getUpdateTime());
        if (progress != null) {
            vo.setContinueChapterId(progress.getChapterId());
            vo.setContinueChapterName(resolveChapterName(work.getWorkType(), progress.getChapterId()));
            vo.setContinueProgress(progress.getProgressPercent());
            vo.setLatestChapterName(vo.getContinueChapterName());
        }
        return vo;
    }

    /**
     * 组装阅读历史项视图对象。
     */
    private AppHistoryItemVo toHistoryItemVo(ReaderReadingHistory history, ReaderWork work) {
        if (work == null) {
            return null;
        }
        AppHistoryItemVo vo = new AppHistoryItemVo();
        vo.setHistoryId(history.getId());
        vo.setWorkId(history.getWorkId());
        vo.setTitle(work.getTitle());
        vo.setWorkType(work.getWorkType());
        vo.setChapterId(history.getChapterId());
        fillHistoryChapterInfo(vo, work.getWorkType(), history.getChapterId());
        vo.setPageNo(parsePageNo(history.getLocationValue()));
        vo.setLocationValue(history.getLocationValue());
        vo.setClientType(history.getClientType());
        vo.setReadAt(history.getReadAt());
        return vo;
    }

    /**
     * 回填历史记录对应的章节标题和序号，避免前端只能根据章节ID再次猜测章节位置。
     */
    private void fillHistoryChapterInfo(AppHistoryItemVo vo, String workType, Long chapterId) {
        if (chapterId == null) {
            return;
        }
        if (WorkType.NOVEL.name().equals(workType)) {
            ReaderNovelChapter chapter = novelChapterMapper.selectById(chapterId);
            if (chapter != null) {
                vo.setChapterName(chapter.getChapterName());
                vo.setChapterNo(chapter.getChapterNo());
            }
            return;
        }
        if (WorkType.COMIC.name().equals(workType)) {
            ReaderComicChapter chapter = comicChapterMapper.selectById(chapterId);
            if (chapter != null) {
                vo.setChapterName(chapter.getChapterName());
                vo.setChapterNo(chapter.getChapterNo());
            }
        }
    }

    /**
     * 按作品类型解析章节名。
     */
    private String resolveChapterName(String workType, Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        if (WorkType.NOVEL.name().equals(workType)) {
            ReaderNovelChapter chapter = novelChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        if (WorkType.COMIC.name().equals(workType)) {
            ReaderComicChapter chapter = comicChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        return null;
    }

    /**
     * 从定位值中解析页码。
     */
    private Integer parsePageNo(String locationValue) {
        if (locationValue == null || locationValue.isBlank()) {
            return null;
        }
        if (locationValue.stripLeading().startsWith("{")) {
            try {
                JsonNode location = READER_LOCATION_MAPPER.readTree(locationValue);
                JsonNode pageNo = location == null ? null : location.get("pageNo");
                return pageNo == null || !pageNo.isNumber() ? null : pageNo.asInt();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        try {
            return Integer.parseInt(locationValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 选择同一作品的最新阅读进度。
     */
    private ReaderReadingProgress pickLatestProgress(ReaderReadingProgress left, ReaderReadingProgress right) {
        return Comparator.comparing(ReaderReadingProgress::getProgressUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .compare(left, right) >= 0 ? left : right;
    }

    /**
     * 校验并返回当前登录用户ID。
     */
    private Long requireCurrentUserId() {
        return visitorAccountService.requireCurrentReaderId();
    }

    /**
     * 校验作品是否处于已发布状态。
     */
    private ReaderWork requirePublishedWork(Long workId) {
        ReaderWork work = readerWorkMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getId, workId)
            .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (work == null) {
            throw new ServiceException(WORK_NOT_FOUND_MESSAGE);
        }
        return work;
    }

    /**
     * 构造书架查询条件。
     */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReaderBookshelf> buildBookshelfQuery(Long userId, String sortBy) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReaderBookshelf> query = Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId);
        if ("joined".equalsIgnoreCase(sortBy)) {
            return query.orderByDesc(ReaderBookshelf::getCreateTime)
                .orderByDesc(ReaderBookshelf::getId);
        }
        if ("updated".equalsIgnoreCase(sortBy)) {
            return query.orderByDesc(ReaderBookshelf::getUpdateTime)
                .orderByDesc(ReaderBookshelf::getId);
        }
        return query.orderByDesc(ReaderBookshelf::getTopPin)
            .orderByAsc(ReaderBookshelf::getSortNo)
            .orderByDesc(ReaderBookshelf::getUpdateTime)
            .orderByDesc(ReaderBookshelf::getId);
    }

    /**
     * 获取当前用户书架最大排序值。
     */
    private Integer nextBookshelfSortNo(Long userId) {
        ReaderBookshelf latest = bookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .orderByDesc(ReaderBookshelf::getSortNo)
            .orderByDesc(ReaderBookshelf::getId)
            .last("limit 1"));
        return latest == null || latest.getSortNo() == null ? 1 : latest.getSortNo() + 1;
    }

    /**
     * 按用户和作品ID读取书架记录。
     */
    private ReaderBookshelf requireBookshelf(Long userId, Long workId) {
        ReaderBookshelf bookshelf = bookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .eq(ReaderBookshelf::getWorkId, workId)
            .last("limit 1"));
        if (bookshelf == null) {
            throw new ServiceException("作品不在书架中");
        }
        return bookshelf;
    }
}
