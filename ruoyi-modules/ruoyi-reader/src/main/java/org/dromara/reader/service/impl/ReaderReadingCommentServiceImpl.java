package org.dromara.reader.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderReadingComment;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.ReaderVisitorAccount;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderReadingCommentSubmitBo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppReadingCommentVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderReadingCommentMapper;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.dromara.reader.mapper.ReaderVisitorAccountMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderPointsService;
import org.dromara.reader.service.IReaderReadingCommentService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 阅读点评服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReaderReadingCommentServiceImpl implements IReaderReadingCommentService {

    /**
     * 点评状态：可见。
     */
    private static final String STATUS_VISIBLE = "VISIBLE";

    /**
     * 点评访问入口。
     */
    private final ReaderReadingCommentMapper readingCommentMapper;

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
     * 访客账户解析服务。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 用户扩展资料访问入口。
     */
    private final ReaderUserProfileMapper userProfileMapper;

    /**
     * 访客账户访问入口。
     */
    private final ReaderVisitorAccountMapper visitorAccountMapper;

    /**
     * 阅读积分服务。
     */
    private final IReaderPointsService pointsService;

    /**
     * 查询阅读点评列表。
     */
    @Override
    public AppPageVo<AppReadingCommentVo> listComments(Long workId, Long chapterId, Integer pageNum, Integer pageSize) {
        List<AppReadingCommentVo> list = readingCommentMapper.selectList(Wrappers.<ReaderReadingComment>lambdaQuery()
                .eq(workId != null, ReaderReadingComment::getWorkId, workId)
                .eq(chapterId != null, ReaderReadingComment::getChapterId, chapterId)
                .eq(ReaderReadingComment::getStatus, STATUS_VISIBLE)
                .orderByDesc(ReaderReadingComment::getCreateTime)
                .orderByDesc(ReaderReadingComment::getId))
            .stream()
            .map(this::toVo)
            .toList();
        return toPage(list, pageNum, pageSize);
    }

    /**
     * 提交阅读点评。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitComment(ReaderReadingCommentSubmitBo bo) {
        if (bo == null || bo.getWorkId() == null || bo.getChapterId() == null || StrUtil.isBlank(bo.getCommentContent())) {
            throw new ServiceException("作品、章节和点评内容不能为空");
        }
        ReaderWork work = requirePublishedWork(bo.getWorkId());
        requireReadableChapter(work, bo.getChapterId());

        Long readerId = visitorAccountService.requireCurrentReaderId();
        String accountType = visitorAccountService.resolveCurrentAccountType();
        ReaderReadingComment comment = new ReaderReadingComment();
        comment.setReaderId(readerId);
        comment.setAccountType(accountType);
        comment.setWorkId(bo.getWorkId());
        comment.setChapterId(bo.getChapterId());
        comment.setQuoteText(StrUtil.trimToNull(bo.getQuoteText()));
        comment.setQuoteStart(bo.getQuoteStart());
        comment.setQuoteEnd(bo.getQuoteEnd());
        comment.setCommentContent(bo.getCommentContent().trim());
        comment.setScore(bo.getScore());
        comment.setLikeCount(0);
        comment.setStatus(STATUS_VISIBLE);
        readingCommentMapper.insert(comment);
        pointsService.claimTask("review");
        return comment.getId();
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
     * 读取可点评章节。
     */
    private void requireReadableChapter(ReaderWork work, Long chapterId) {
        if (work == null || chapterId == null) {
            throw new ServiceException("章节不存在");
        }
        if ("NOVEL".equals(work.getWorkType())) {
            ReaderNovelChapter chapter = novelChapterMapper.selectOne(Wrappers.<ReaderNovelChapter>lambdaQuery()
                .eq(ReaderNovelChapter::getId, chapterId)
                .eq(ReaderNovelChapter::getWorkId, work.getId())
                .eq(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name()));
            if (chapter == null) {
                throw new ServiceException("章节不存在");
            }
            return;
        }
        if ("COMIC".equals(work.getWorkType())) {
            ReaderComicChapter chapter = comicChapterMapper.selectOne(Wrappers.<ReaderComicChapter>lambdaQuery()
                .eq(ReaderComicChapter::getId, chapterId)
                .eq(ReaderComicChapter::getWorkId, work.getId())
                .eq(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name()));
            if (chapter == null) {
                throw new ServiceException("章节不存在");
            }
            return;
        }
        throw new ServiceException("章节不存在");
    }

    /**
     * 转换为前端视图对象。
     */
    private AppReadingCommentVo toVo(ReaderReadingComment comment) {
        AppReadingCommentVo vo = new AppReadingCommentVo();
        vo.setCommentId(comment.getId());
        vo.setWorkId(comment.getWorkId());
        vo.setChapterId(comment.getChapterId());
        vo.setQuoteText(comment.getQuoteText());
        vo.setQuoteStart(comment.getQuoteStart());
        vo.setQuoteEnd(comment.getQuoteEnd());
        vo.setCommentContent(comment.getCommentContent());
        vo.setScore(comment.getScore());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreateTime(comment.getCreateTime());
        fillReaderMeta(comment.getReaderId(), comment.getAccountType(), vo);
        return vo;
    }

    /**
     * 填充作者信息。
     */
    private void fillReaderMeta(Long readerId, String accountType, AppReadingCommentVo vo) {
        if (readerId == null) {
            return;
        }
        if (Objects.equals("USER", accountType)) {
            ReaderUserProfile profile = userProfileMapper.selectById(readerId);
            vo.setNickName(profile == null || StrUtil.isBlank(profile.getNickName()) ? "读者" : profile.getNickName());
            vo.setAvatarStyle(profile == null ? null : profile.getAvatarStyle());
            return;
        }
        ReaderVisitorAccount account = visitorAccountMapper.selectById(readerId);
        vo.setNickName(account == null || StrUtil.isBlank(account.getNickName()) ? "游客" : account.getNickName());
        vo.setAvatarStyle(account == null ? null : account.getAvatarStyle());
    }

    /**
     * 切分页。
     */
    private AppPageVo<AppReadingCommentVo> toPage(List<AppReadingCommentVo> list, Integer pageNum, Integer pageSize) {
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int fromIndex = Math.min((currentPage - 1) * currentSize, list.size());
        int toIndex = Math.min(fromIndex + currentSize, list.size());
        AppPageVo<AppReadingCommentVo> vo = new AppPageVo<>();
        vo.setList(list.subList(fromIndex, toIndex));
        vo.setTotal((long) list.size());
        vo.setPageNum(currentPage);
        vo.setPageSize(currentSize);
        vo.setTotalPages((int) Math.ceil((double) list.size() / currentSize));
        return vo;
    }
}
