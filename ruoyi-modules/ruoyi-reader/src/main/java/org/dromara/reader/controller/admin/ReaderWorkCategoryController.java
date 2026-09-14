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
import org.dromara.reader.domain.ReaderWorkCategory;
import org.dromara.reader.mapper.ReaderWorkCategoryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

import java.util.Locale;

/** 作品内容分类管理，供运营维护采集和作品展示使用的分类字典。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/work-categories")
public class ReaderWorkCategoryController {

    private final ReaderWorkCategoryMapper categoryMapper;

    @GetMapping("/list")
    @SaCheckPermission("reader:category:list")
    public R<PageResult<ReaderWorkCategory>> list(String categoryName, String status, PageQuery pageQuery) {
        Page<ReaderWorkCategory> page = categoryMapper.selectPage(pageQuery.build(), Wrappers.<ReaderWorkCategory>lambdaQuery()
            .like(StringUtils.isNotBlank(categoryName), ReaderWorkCategory::getCategoryName, categoryName)
            .eq(StringUtils.isNotBlank(status), ReaderWorkCategory::getStatus, status)
            .orderByAsc(ReaderWorkCategory::getCategoryName));
        return R.ok(PageResult.build(page.getRecords(), page.getTotal()));
    }

    @PostMapping
    @SaCheckPermission("reader:category:list")
    public R<Long> add(@RequestBody ReaderWorkCategory category) {
        normalizeAndValidate(category);
        category.setId(null);
        category.setSourceType("MANUAL");
        category.setStatus(StringUtils.isBlank(category.getStatus()) ? "1" : category.getStatus());
        categoryMapper.insert(category);
        return R.ok(category.getId());
    }

    @PutMapping("/{categoryId}")
    @SaCheckPermission("reader:category:list")
    public R<Void> edit(@PathVariable Long categoryId, @RequestBody ReaderWorkCategory category) {
        if (categoryMapper.selectById(categoryId) == null) throw new ServiceException("作品分类不存在");
        category.setId(categoryId);
        normalizeAndValidate(category);
        categoryMapper.updateById(category);
        return R.ok();
    }

    @PostMapping("/{categoryId}/status/{status}")
    @SaCheckPermission("reader:category:list")
    public R<Void> updateStatus(@PathVariable Long categoryId, @PathVariable String status) {
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("分类状态只能为启用或停用");
        ReaderWorkCategory category = categoryMapper.selectById(categoryId);
        if (category == null) throw new ServiceException("作品分类不存在");
        category.setStatus(status);
        categoryMapper.updateById(category);
        return R.ok();
    }

    /** 批量启用或停用作品分类。 */
    @PostMapping("/batch/status/{status}")
    @SaCheckPermission("reader:category:list")
    public R<ReaderBatchActionResult> batchStatus(@PathVariable String status, @RequestBody java.util.List<Long> ids) {
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("分类状态只能为启用或停用");
        return R.ok(ReaderBatchActionResult.execute(ids, id -> {
            try {
                ReaderWorkCategory category = categoryMapper.selectById(id);
                if (category == null) return "作品分类不存在";
                category.setStatus(status);
                categoryMapper.updateById(category);
                return null;
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }));
    }

    private void normalizeAndValidate(ReaderWorkCategory category) {
        if (category == null || StringUtils.isBlank(category.getCategoryName())) throw new ServiceException("分类名称不能为空");
        String name = normalizeCategoryName(category.getCategoryName());
        if (name.length() > 64) throw new ServiceException("分类名称不能超过64个字符");
        category.setCategoryName(name);
        category.setNormalizedName(name.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    /** 来源站点常用中括号包裹分类，去除外层标记避免生成重复分类。 */
    private String normalizeCategoryName(String value) {
        String name = value.trim().replaceAll("\\s+", " ");
        if (name.length() > 2 && name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name;
    }
}
