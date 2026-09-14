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
import org.dromara.reader.domain.ReaderReadingComment;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderBatchStatusBo;
import org.dromara.reader.domain.bo.ReaderReadingCommentAdminQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.domain.vo.admin.ReaderReadingCommentAdminVo;
import org.dromara.reader.mapper.ReaderReadingCommentMapper;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** H5 书评管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/h5/comments")
public class ReaderReadingCommentAdminController {
    private final ReaderReadingCommentMapper commentMapper;
    private final ReaderUserProfileMapper userProfileMapper;
    private final ReaderWorkMapper workMapper;

    @GetMapping("/list")
    @SaCheckPermission("reader:h5-comment:list")
    public R<PageResult<ReaderReadingCommentAdminVo>> list(ReaderReadingCommentAdminQueryBo bo, PageQuery pageQuery) {
        String keyword = bo == null ? null : bo.getKeyword();
        Page<ReaderReadingComment> page = commentMapper.selectPage(pageQuery.build(), Wrappers.<ReaderReadingComment>lambdaQuery()
            .and(StringUtils.isNotBlank(keyword), q -> q.like(ReaderReadingComment::getCommentContent, keyword).or().like(ReaderReadingComment::getQuoteText, keyword))
            .eq(bo != null && StringUtils.isNotBlank(bo.getStatus()), ReaderReadingComment::getStatus, bo.getStatus())
            .eq(bo != null && bo.getWorkId() != null, ReaderReadingComment::getWorkId, bo.getWorkId())
            .orderByDesc(ReaderReadingComment::getCreateTime)
            .orderByDesc(ReaderReadingComment::getId));
        return R.ok(PageResult.build(page.getRecords().stream().map(this::toVo).toList(), page.getTotal()));
    }

    @PostMapping("/{commentId}/status/{status}")
    @SaCheckPermission("reader:h5-comment:status")
    public R<Void> status(@PathVariable Long commentId, @PathVariable String status) {
        updateStatus(commentId, status);
        return R.ok();
    }

    @PostMapping("/batch/status")
    @SaCheckPermission("reader:h5-comment:status")
    public R<ReaderBatchActionResult> batchStatus(@RequestBody ReaderBatchStatusBo bo) {
        if (bo == null || bo.getIds() == null) throw new ServiceException("书评列表不能为空");
        return R.ok(ReaderBatchActionResult.execute(bo.getIds(), id -> {
            try {
                updateStatus(id, bo.getStatus());
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    private void updateStatus(Long id, String status) {
        if (!"VISIBLE".equals(status) && !"HIDDEN".equals(status) && !"DELETED".equals(status)) {
            throw new ServiceException("书评状态不合法");
        }
        ReaderReadingComment comment = commentMapper.selectById(id);
        if (comment == null) throw new ServiceException("书评不存在");
        comment.setStatus(status);
        commentMapper.updateById(comment);
    }

    private ReaderReadingCommentAdminVo toVo(ReaderReadingComment comment) {
        ReaderReadingCommentAdminVo vo = new ReaderReadingCommentAdminVo();
        vo.setId(comment.getId());
        vo.setReaderId(comment.getReaderId());
        vo.setAccountType(comment.getAccountType());
        vo.setWorkId(comment.getWorkId());
        vo.setChapterId(comment.getChapterId());
        vo.setQuoteText(comment.getQuoteText());
        vo.setCommentContent(comment.getCommentContent());
        vo.setScore(comment.getScore());
        vo.setLikeCount(comment.getLikeCount());
        vo.setStatus(comment.getStatus());
        vo.setCreateTime(comment.getCreateTime());
        vo.setUpdateTime(comment.getUpdateTime());
        ReaderWork work = comment.getWorkId() == null ? null : workMapper.selectById(comment.getWorkId());
        ReaderUserProfile profile = comment.getReaderId() == null ? null : userProfileMapper.selectById(comment.getReaderId());
        vo.setWorkTitle(work == null ? null : work.getTitle());
        vo.setNickName(profile == null ? null : profile.getNickName());
        return vo;
    }
}
