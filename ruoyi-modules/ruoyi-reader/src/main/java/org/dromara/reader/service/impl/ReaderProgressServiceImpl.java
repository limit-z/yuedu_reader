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
import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.domain.vo.app.AppReadingProgressVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderBookshelfMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingHistoryMapper;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderProgressService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.service.cache.ReaderProgressCacheService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

/**
 * 阅读器阅读进度服务实现，负责进度保存、历史更新与书架最近阅读时间同步。
 */
@RequiredArgsConstructor
@Service
public class ReaderProgressServiceImpl implements IReaderProgressService {

    private static final JsonMapper READER_LOCATION_MAPPER = JsonMapper.builder().build();

    /**
     * 作品不存在时统一复用的提示语。
     */
    private static final String WORK_NOT_FOUND_MESSAGE = "作品不存在";

    /**
     * 作品主表访问入口，负责进度保存前的作品状态校验。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 阅读进度访问入口，负责进度记录的新增与更新。
     */
    private final ReaderReadingProgressMapper readingProgressMapper;

    /**
     * 阅读历史访问入口，负责最近阅读轨迹维护。
     */
    private final ReaderReadingHistoryMapper readingHistoryMapper;

    /**
     * 书架访问入口，负责同步最近阅读时间到书架排序。
     */
    private final ReaderBookshelfMapper bookshelfMapper;

    /**
     * 小说章节访问入口，负责校验小说章节是否属于当前作品。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 漫画章节访问入口，负责校验漫画章节是否属于当前作品。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 阅读进度缓存服务，负责多端快速读取与延迟刷库。
     */
    private final ReaderProgressCacheService progressCacheService;

    /**
     * 访客账户服务入口，负责把登录用户与游客态统一为同一套读者主体。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 保存阅读进度。
     */
    @Override
    public void saveProgress(ReaderProgressBo bo) {
        Long userId = requireCurrentUserId();
        ReaderWork work = requirePublishedWork(bo.getWorkId());
        LocalDateTime updatedAt = bo.getUpdatedAt() == null ? LocalDateTime.now() : bo.getUpdatedAt();
        Integer readPercent = resolveReadPercent(bo);
        String locationValue = resolveLocationValue(bo);

        // 进度写入按 userId + workId 幂等更新，确保多端重复同步时只保留最新快照。
        ReaderReadingProgress existingProgress = readingProgressMapper.selectOne(Wrappers.<ReaderReadingProgress>lambdaQuery()
            .eq(ReaderReadingProgress::getUserId, userId)
            .eq(ReaderReadingProgress::getWorkId, bo.getWorkId()));
        if (existingProgress == null) {
            ReaderReadingProgress progress = new ReaderReadingProgress();
            fillProgress(progress, userId, work, bo, readPercent, locationValue, updatedAt);
            readingProgressMapper.insert(progress);
            progressCacheService.cacheProgress(progress);
        } else {
            // 如果旧端上报了更早的时间点，直接忽略，避免把用户的新进度回滚掉。
            if (existingProgress.getProgressUpdatedAt() != null && updatedAt.isBefore(existingProgress.getProgressUpdatedAt())) {
                return;
            }
            fillProgress(existingProgress, userId, work, bo, readPercent, locationValue, updatedAt);
            readingProgressMapper.updateById(existingProgress);
            progressCacheService.cacheProgress(existingProgress);
        }

        upsertHistory(userId, work, bo, locationValue, updatedAt);
        touchBookshelf(userId, bo.getWorkId(), updatedAt);
    }

    /**
     * 获取作品阅读进度。
     */
    @Override
    public AppReadingProgressVo getProgress(Long workId) {
        Long userId = requireCurrentUserId();
        // 先读缓存再回源数据库，保证详情页和阅读页反复打开时能快速拿到最近进度。
        ReaderReadingProgress progress = progressCacheService.getProgress(userId, workId);
        if (progress == null) {
            progress = readingProgressMapper.selectOne(Wrappers.<ReaderReadingProgress>lambdaQuery()
                .eq(ReaderReadingProgress::getUserId, userId)
                .eq(ReaderReadingProgress::getWorkId, workId));
            if (progress != null) {
                progressCacheService.cacheSnapshot(progress);
            }
        }
        if (progress == null) {
            return null;
        }
        ReaderWork work = requirePublishedWork(workId);
        AppReadingProgressVo vo = new AppReadingProgressVo();
        vo.setWorkId(progress.getWorkId());
        vo.setChapterId(progress.getChapterId());
        vo.setPageNo(parsePageNo(progress.getLocationValue()));
        vo.setLocationValue(progress.getLocationValue());
        vo.setReadPercent(progress.getProgressPercent());
        vo.setClientType(progress.getClientType());
        vo.setUpdatedAt(progress.getProgressUpdatedAt());
        vo.setWorkType(work.getWorkType());
        fillChapterInfo(vo, work.getWorkType(), progress.getChapterId());
        return vo;
    }

    /**
     * 填充持久化阅读进度实体。
     */
    private void fillProgress(ReaderReadingProgress progress, Long userId, ReaderWork work, ReaderProgressBo bo,
                              Integer readPercent, String locationValue, LocalDateTime updatedAt) {
        // locationValue 统一承载页码或其它阅读位置，便于小说与漫画共用同一套进度模型。
        progress.setUserId(userId);
        progress.setWorkId(bo.getWorkId());
        progress.setChapterId(bo.getChapterId());
        progress.setContentType(work.getWorkType());
        progress.setLocationValue(locationValue);
        progress.setProgressPercent(readPercent);
        progress.setClientType(bo.getClientType());
        progress.setProgressUpdatedAt(updatedAt);
    }

    /**
     * 新增或更新阅读历史落点。
     */
    private void upsertHistory(Long userId, ReaderWork work, ReaderProgressBo bo, String locationValue, LocalDateTime updatedAt) {
        // 阅读历史只保留每本作品最近一次阅读落点，供“继续阅读”与历史页复用。
        ReaderReadingHistory history = readingHistoryMapper.selectOne(Wrappers.<ReaderReadingHistory>lambdaQuery()
            .eq(ReaderReadingHistory::getUserId, userId)
            .eq(ReaderReadingHistory::getWorkId, bo.getWorkId())
            .last("limit 1"));
        if (history == null) {
            history = new ReaderReadingHistory();
            history.setUserId(userId);
            history.setWorkId(bo.getWorkId());
            history.setChapterId(bo.getChapterId());
            history.setContentType(work.getWorkType());
            history.setLocationValue(locationValue);
            history.setClientType(bo.getClientType());
            history.setReadAt(updatedAt);
            readingHistoryMapper.insert(history);
            return;
        }
        history.setChapterId(bo.getChapterId());
        history.setContentType(work.getWorkType());
        history.setLocationValue(locationValue);
        history.setClientType(bo.getClientType());
        history.setReadAt(updatedAt);
        readingHistoryMapper.updateById(history);
    }

    /**
     * 刷新书架最近阅读时间。
     */
    private void touchBookshelf(Long userId, Long workId, LocalDateTime updatedAt) {
        // 书架最近更新时间跟随阅读进度推进，方便前端按“最近阅读”排序。
        ReaderBookshelf bookshelf = bookshelfMapper.selectOne(Wrappers.<ReaderBookshelf>lambdaQuery()
            .eq(ReaderBookshelf::getUserId, userId)
            .eq(ReaderBookshelf::getWorkId, workId));
        if (bookshelf == null) {
            return;
        }
        bookshelf.setUpdateTime(updatedAt);
        bookshelfMapper.updateById(bookshelf);
    }

    /**
     * 回填章节名称与序号信息。
     */
    private void fillChapterInfo(AppReadingProgressVo vo, String workType, Long chapterId) {
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
     * 解析标准化阅读百分比。
     */
    private Integer resolveReadPercent(ReaderProgressBo bo) {
        if (bo.getReadPercent() != null) {
            return bo.getReadPercent();
        }
        return bo.getProgressPercent() == null ? 0 : bo.getProgressPercent();
    }

    /**
     * 解析统一的阅读定位值。
     */
    private String resolveLocationValue(ReaderProgressBo bo) {
        if (bo.getLocationValue() != null && !bo.getLocationValue().isBlank()) {
            return bo.getLocationValue();
        }
        if (bo.getPageNo() != null) {
            return String.valueOf(bo.getPageNo());
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
}
