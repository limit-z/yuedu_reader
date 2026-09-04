package org.dromara.reader.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.reader.domain.ReaderUserFeedback;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.ReaderVisitorAccount;
import org.dromara.reader.domain.bo.ReaderFeedbackQueryBo;
import org.dromara.reader.domain.bo.ReaderFeedbackReplyBo;
import org.dromara.reader.domain.bo.ReaderFeedbackStatusBo;
import org.dromara.reader.domain.bo.ReaderFeedbackSubmitBo;
import org.dromara.reader.domain.vo.admin.ReaderFeedbackAdminVo;
import org.dromara.reader.domain.vo.app.ReaderFeedbackRecordVo;
import org.dromara.reader.mapper.ReaderAccountBindMapper;
import org.dromara.reader.mapper.ReaderUserFeedbackMapper;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.dromara.reader.service.IReaderFeedbackService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅读器反馈服务实现，负责用户反馈提交、用户侧查询与后台工单处理。
 */
@RequiredArgsConstructor
@Service
public class ReaderFeedbackServiceImpl implements IReaderFeedbackService {

    /**
     * 待处理状态，表示工单刚提交还未被管理员接手。
     */
    private static final String STATUS_PENDING = "PENDING";

    /**
     * 已回复状态，表示管理员已经给出明确回复内容。
     */
    private static final String STATUS_REPLIED = "REPLIED";

    /**
     * 反馈访问入口，负责用户侧查询、提交和管理端状态更新。
     */
    private final ReaderUserFeedbackMapper readerUserFeedbackMapper;

    /**
     * 访客账户服务入口，负责统一解析当前读者主体与游客资料快照。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 用户扩展资料访问入口，负责读取登录用户侧昵称等基础信息。
     */
    private final ReaderUserProfileMapper readerUserProfileMapper;

    /**
     * 账号绑定访问入口，负责读取手机号、邮箱等联系方式。
     */
    private final ReaderAccountBindMapper readerAccountBindMapper;

    /**
     * 查询当前读者自己的反馈记录。
     */
    @Override
    public List<ReaderFeedbackRecordVo> listMyFeedback() {
        Long readerId = visitorAccountService.requireCurrentReaderId();
        String accountType = visitorAccountService.resolveCurrentAccountType();
        return readerUserFeedbackMapper.selectList(Wrappers.<ReaderUserFeedback>lambdaQuery()
                .eq(ReaderUserFeedback::getReaderId, readerId)
                .eq(ReaderUserFeedback::getAccountType, accountType)
                .orderByDesc(ReaderUserFeedback::getCreateTime)
                .orderByDesc(ReaderUserFeedback::getId))
            .stream()
            .map(this::toAppVo)
            .toList();
    }

    /**
     * 提交一条新的反馈记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitFeedback(ReaderFeedbackSubmitBo bo) {
        if (bo == null || StrUtil.isBlank(bo.getFeedbackType()) || StrUtil.isBlank(bo.getFeedbackContent())) {
            throw new ServiceException("反馈类型和反馈内容不能为空");
        }

        ReaderUserFeedback feedback = new ReaderUserFeedback();
        fillReaderSnapshot(feedback);
        feedback.setFeedbackType(bo.getFeedbackType());
        feedback.setFeedbackContent(bo.getFeedbackContent().trim());
        feedback.setStatus(STATUS_PENDING);
        readerUserFeedbackMapper.insert(feedback);
        return feedback.getId();
    }

    /**
     * 管理端分页查询反馈列表。
     */
    @Override
    public PageResult<ReaderFeedbackAdminVo> queryAdminPageList(ReaderFeedbackQueryBo bo, PageQuery pageQuery) {
        Page<ReaderUserFeedback> page = readerUserFeedbackMapper.selectPage(pageQuery.build(), buildAdminQueryWrapper(bo));
        List<ReaderFeedbackAdminVo> rows = page.getRecords().stream()
            .map(this::toAdminVo)
            .toList();
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 管理员回复指定反馈工单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long feedbackId, ReaderFeedbackReplyBo bo) {
        ReaderUserFeedback feedback = requireFeedback(feedbackId);
        if (bo == null || StrUtil.isBlank(bo.getReplyContent())) {
            throw new ServiceException("回复内容不能为空");
        }

        // 回复动作会同步更新回复人、回复时间和工单状态，方便用户侧直接看到处理结果。
        feedback.setReplyContent(bo.getReplyContent().trim());
        feedback.setReplyBy(LoginHelper.getUserId());
        feedback.setReplyTime(LocalDateTime.now());
        feedback.setStatus(StrUtil.blankToDefault(bo.getStatus(), STATUS_REPLIED));
        readerUserFeedbackMapper.updateById(feedback);
    }

    /**
     * 管理员更新指定反馈工单状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long feedbackId, ReaderFeedbackStatusBo bo) {
        ReaderUserFeedback feedback = requireFeedback(feedbackId);
        if (bo == null || StrUtil.isBlank(bo.getStatus())) {
            throw new ServiceException("目标状态不能为空");
        }
        feedback.setStatus(bo.getStatus());
        readerUserFeedbackMapper.updateById(feedback);
    }

    /**
     * 构造管理端查询条件，支持状态、类型和关键词筛选。
     */
    private LambdaQueryWrapper<ReaderUserFeedback> buildAdminQueryWrapper(ReaderFeedbackQueryBo bo) {
        LambdaQueryWrapper<ReaderUserFeedback> lqw = Wrappers.lambdaQuery();
        if (bo == null) {
            lqw.orderByDesc(ReaderUserFeedback::getCreateTime).orderByDesc(ReaderUserFeedback::getId);
            return lqw;
        }
        lqw.eq(StrUtil.isNotBlank(bo.getStatus()), ReaderUserFeedback::getStatus, bo.getStatus());
        lqw.eq(StrUtil.isNotBlank(bo.getFeedbackType()), ReaderUserFeedback::getFeedbackType, bo.getFeedbackType());
        if (StrUtil.isNotBlank(bo.getKeyword())) {
            // 关键词统一匹配昵称、手机号、微信号和反馈正文，减少管理员切换筛选维度的成本。
            lqw.and(wrapper -> wrapper
                .like(ReaderUserFeedback::getNickName, bo.getKeyword())
                .or()
                .like(ReaderUserFeedback::getContactMobile, bo.getKeyword())
                .or()
                .like(ReaderUserFeedback::getContactWechat, bo.getKeyword())
                .or()
                .like(ReaderUserFeedback::getFeedbackContent, bo.getKeyword()));
        }
        lqw.orderByDesc(ReaderUserFeedback::getCreateTime);
        lqw.orderByDesc(ReaderUserFeedback::getId);
        return lqw;
    }

    /**
     * 把当前读者的昵称、手机号、微信号等资料快照写入反馈工单。
     */
    private void fillReaderSnapshot(ReaderUserFeedback feedback) {
        Long readerId = visitorAccountService.requireCurrentReaderId();
        String accountType = visitorAccountService.resolveCurrentAccountType();
        feedback.setReaderId(readerId);
        feedback.setAccountType(accountType);

        if ("USER".equals(accountType)) {
            ReaderUserProfile profile = readerUserProfileMapper.selectById(readerId);
            feedback.setNickName(profile == null || StrUtil.isBlank(profile.getNickName()) ? "读者" : profile.getNickName());
            feedback.setContactMobile(resolveBindValue(readerId, "PHONE"));
            feedback.setContactWechat(resolveBindValue(readerId, "WECHAT_NO"));
            return;
        }

        ReaderVisitorAccount account = visitorAccountService.getCurrentVisitorAccount();
        feedback.setNickName(account == null || StrUtil.isBlank(account.getNickName()) ? "游客" : account.getNickName());
        feedback.setContactMobile(account == null ? null : account.getMobile());
        feedback.setContactWechat(account == null ? null : account.getWechatNo());
    }

    /**
     * 读取当前账号的绑定值。
     */
    private String resolveBindValue(Long accountId, String bindType) {
        org.dromara.reader.domain.ReaderAccountBind bind = readerAccountBindMapper.selectOne(Wrappers.<org.dromara.reader.domain.ReaderAccountBind>lambdaQuery()
            .eq(org.dromara.reader.domain.ReaderAccountBind::getAccountId, accountId)
            .eq(org.dromara.reader.domain.ReaderAccountBind::getBindType, bindType)
            .last("limit 1"));
        return bind == null ? null : bind.getBindKey();
    }

    /**
     * 校验反馈记录是否存在。
     */
    private ReaderUserFeedback requireFeedback(Long feedbackId) {
        ReaderUserFeedback feedback = readerUserFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new ServiceException("反馈记录不存在");
        }
        return feedback;
    }

    /**
     * 转换为用户侧反馈记录视图。
     */
    private ReaderFeedbackRecordVo toAppVo(ReaderUserFeedback feedback) {
        ReaderFeedbackRecordVo vo = new ReaderFeedbackRecordVo();
        vo.setFeedbackId(feedback.getId());
        vo.setFeedbackType(feedback.getFeedbackType());
        vo.setFeedbackContent(feedback.getFeedbackContent());
        vo.setNickName(feedback.getNickName());
        vo.setContactMobile(feedback.getContactMobile());
        vo.setContactWechat(feedback.getContactWechat());
        vo.setStatus(feedback.getStatus());
        vo.setReplyContent(feedback.getReplyContent());
        vo.setReplyTime(feedback.getReplyTime());
        vo.setCreateTime(feedback.getCreateTime());
        return vo;
    }

    /**
     * 转换为管理端反馈工单视图。
     */
    private ReaderFeedbackAdminVo toAdminVo(ReaderUserFeedback feedback) {
        ReaderFeedbackAdminVo vo = new ReaderFeedbackAdminVo();
        vo.setFeedbackId(feedback.getId());
        vo.setReaderId(feedback.getReaderId());
        vo.setAccountType(feedback.getAccountType());
        vo.setFeedbackType(feedback.getFeedbackType());
        vo.setFeedbackContent(feedback.getFeedbackContent());
        vo.setNickName(feedback.getNickName());
        vo.setContactMobile(feedback.getContactMobile());
        vo.setContactWechat(feedback.getContactWechat());
        vo.setStatus(feedback.getStatus());
        vo.setReplyContent(feedback.getReplyContent());
        vo.setReplyBy(feedback.getReplyBy());
        vo.setReplyTime(feedback.getReplyTime());
        vo.setCreateTime(feedback.getCreateTime());
        return vo;
    }
}
