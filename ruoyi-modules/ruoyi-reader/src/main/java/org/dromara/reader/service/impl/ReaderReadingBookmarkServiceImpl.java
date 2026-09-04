package org.dromara.reader.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderReadingBookmark;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderReadingBookmarkSaveBo;
import org.dromara.reader.domain.vo.app.AppReadingBookmarkVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingBookmarkMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderReadingBookmarkService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阅读书签服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReaderReadingBookmarkServiceImpl implements IReaderReadingBookmarkService {

    /**
     * 书签访问入口。
     */
    private final ReaderReadingBookmarkMapper bookmarkMapper;

    /**
     * 作品访问入口。
     */
    private final ReaderWorkMapper workMapper;

    /**
     * 小说章节访问入口。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 漫画章节访问入口。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 读者主体解析服务。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 查询书签列表。
     */
    @Override
    public List<AppReadingBookmarkVo> listBookmarks(Long workId) {
        Long userId = visitorAccountService.requireCurrentReaderId();
        List<ReaderReadingBookmark> bookmarks = bookmarkMapper.selectList(Wrappers.<ReaderReadingBookmark>lambdaQuery()
            .eq(ReaderReadingBookmark::getUserId, userId)
            .eq(workId != null, ReaderReadingBookmark::getWorkId, workId)
            .orderByDesc(ReaderReadingBookmark::getCreateTime)
            .orderByDesc(ReaderReadingBookmark::getId));
        if (CollUtil.isEmpty(bookmarks)) {
            return List.of();
        }
        return bookmarks.stream().map(this::toVo).toList();
    }

    /**
     * 新增书签。
     */
    @Override
    public Long saveBookmark(ReaderReadingBookmarkSaveBo bo) {
        if (bo == null || bo.getWorkId() == null || bo.getChapterId() == null) {
            throw new ServiceException("作品和章节不能为空");
        }
        ReaderWork work = requirePublishedWork(bo.getWorkId());
        ReaderReadingBookmark bookmark = new ReaderReadingBookmark();
        bookmark.setUserId(visitorAccountService.requireCurrentReaderId());
        bookmark.setWorkId(work.getId());
        bookmark.setWorkTitle(work.getTitle());
        bookmark.setWorkType(work.getWorkType());
        bookmark.setChapterId(bo.getChapterId());
        bookmark.setChapterName(resolveChapterName(work, bo.getChapterId()));
        bookmark.setChapterNo(resolveChapterNo(work, bo.getChapterId()));
        bookmark.setLocationValue(StrUtil.trimToNull(bo.getLocationValue()));
        ReaderReadingBookmark existing = bookmarkMapper.selectOne(Wrappers.<ReaderReadingBookmark>lambdaQuery()
            .eq(ReaderReadingBookmark::getUserId, bookmark.getUserId())
            .eq(ReaderReadingBookmark::getWorkId, bookmark.getWorkId())
            .eq(ReaderReadingBookmark::getChapterId, bookmark.getChapterId())
            .last("limit 1"));
        if (existing != null) {
            existing.setLocationValue(bookmark.getLocationValue());
            existing.setChapterName(bookmark.getChapterName());
            existing.setChapterNo(bookmark.getChapterNo());
            bookmarkMapper.updateById(existing);
            return existing.getId();
        }
        bookmarkMapper.insert(bookmark);
        return bookmark.getId();
    }

    /**
     * 删除书签。
     */
    @Override
    public void removeBookmark(Long bookmarkId) {
        if (bookmarkId == null) {
            return;
        }
        Long userId = visitorAccountService.requireCurrentReaderId();
        bookmarkMapper.delete(Wrappers.<ReaderReadingBookmark>lambdaQuery()
            .eq(ReaderReadingBookmark::getUserId, userId)
            .eq(ReaderReadingBookmark::getId, bookmarkId));
    }

    /**
     * 读取已发布作品。
     */
    private ReaderWork requirePublishedWork(Long workId) {
        ReaderWork work = workMapper.selectOne(Wrappers.<ReaderWork>lambdaQuery()
            .eq(ReaderWork::getId, workId)
            .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name()));
        if (work == null) {
            throw new ServiceException("作品不存在");
        }
        return work;
    }

    /**
     * 解析章节名称。
     */
    private String resolveChapterName(ReaderWork work, Long chapterId) {
        if (work == null || chapterId == null) {
            return null;
        }
        if ("NOVEL".equals(work.getWorkType())) {
            ReaderNovelChapter chapter = novelChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        if ("COMIC".equals(work.getWorkType())) {
            ReaderComicChapter chapter = comicChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterName();
        }
        return null;
    }

    /**
     * 解析章节序号。
     */
    private Integer resolveChapterNo(ReaderWork work, Long chapterId) {
        if (work == null || chapterId == null) {
            return null;
        }
        if ("NOVEL".equals(work.getWorkType())) {
            ReaderNovelChapter chapter = novelChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterNo();
        }
        if ("COMIC".equals(work.getWorkType())) {
            ReaderComicChapter chapter = comicChapterMapper.selectById(chapterId);
            return chapter == null ? null : chapter.getChapterNo();
        }
        return null;
    }

    /**
     * 转换为前端视图对象。
     */
    private AppReadingBookmarkVo toVo(ReaderReadingBookmark bookmark) {
        AppReadingBookmarkVo vo = new AppReadingBookmarkVo();
        vo.setBookmarkId(bookmark.getId());
        vo.setWorkId(bookmark.getWorkId());
        vo.setWorkTitle(bookmark.getWorkTitle());
        vo.setWorkType(bookmark.getWorkType());
        vo.setChapterId(bookmark.getChapterId());
        vo.setChapterName(bookmark.getChapterName());
        vo.setChapterNo(bookmark.getChapterNo());
        vo.setLocationValue(bookmark.getLocationValue());
        vo.setCreateTime(bookmark.getCreateTime());
        return vo;
    }
}
