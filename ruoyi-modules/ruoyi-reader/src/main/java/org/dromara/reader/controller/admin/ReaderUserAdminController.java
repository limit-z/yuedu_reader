package org.dromara.reader.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.bo.ReaderBatchStatusBo;
import org.dromara.reader.domain.bo.ReaderUserAdminQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.domain.vo.admin.ReaderUserAdminVo;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H5 读者用户管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/h5/users")
public class ReaderUserAdminController {
    private final ReaderUserProfileMapper userProfileMapper;

    @GetMapping("/list")
    @SaCheckPermission("reader:h5-user:list")
    public R<PageResult<ReaderUserAdminVo>> list(ReaderUserAdminQueryBo bo, PageQuery pageQuery) {
        String keyword = bo == null ? null : bo.getKeyword();
        Page<ReaderUserProfile> page = userProfileMapper.selectPage(pageQuery.build(), Wrappers.<ReaderUserProfile>lambdaQuery()
            .and(org.dromara.common.core.utils.StringUtils.isNotBlank(keyword), q -> q
                .like(ReaderUserProfile::getNickName, keyword)
                .or().like(ReaderUserProfile::getAccountId, keyword))
            .eq(bo != null && org.dromara.common.core.utils.StringUtils.isNotBlank(bo.getStatus()), ReaderUserProfile::getStatus, bo.getStatus())
            .eq(bo != null && org.dromara.common.core.utils.StringUtils.isNotBlank(bo.getLastClientType()), ReaderUserProfile::getLastClientType, bo.getLastClientType())
            .orderByDesc(ReaderUserProfile::getLastLoginAt)
            .orderByDesc(ReaderUserProfile::getAccountId));
        return R.ok(PageResult.build(page.getRecords().stream().map(this::toVo).toList(), page.getTotal()));
    }

    @PostMapping("/{accountId}/status/{status}")
    @SaCheckPermission("reader:h5-user:status")
    public R<Void> status(@PathVariable Long accountId, @PathVariable String status) {
        updateStatus(accountId, status);
        return R.ok();
    }

    @PostMapping("/batch/status")
    @SaCheckPermission("reader:h5-user:status")
    public R<ReaderBatchActionResult> batchStatus(@RequestBody ReaderBatchStatusBo bo) {
        if (bo == null || bo.getIds() == null) throw new ServiceException("用户列表不能为空");
        return R.ok(ReaderBatchActionResult.execute(bo.getIds(), id -> {
            try {
                updateStatus(id, bo.getStatus());
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    private void updateStatus(Long accountId, String status) {
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("用户状态只能为正常或停用");
        ReaderUserProfile profile = userProfileMapper.selectById(accountId);
        if (profile == null) throw new ServiceException("读者用户不存在");
        profile.setStatus(status);
        userProfileMapper.updateById(profile);
    }

    private ReaderUserAdminVo toVo(ReaderUserProfile profile) {
        ReaderUserAdminVo vo = new ReaderUserAdminVo();
        vo.setAccountId(profile.getAccountId());
        vo.setNickName(profile.getNickName());
        vo.setGender(profile.getGender());
        vo.setRegion(profile.getRegion());
        vo.setStatus(profile.getStatus());
        vo.setLastClientType(profile.getLastClientType());
        vo.setLastLoginAt(profile.getLastLoginAt());
        vo.setCreateTime(profile.getCreateTime());
        vo.setUpdateTime(profile.getUpdateTime());
        return vo;
    }
}
