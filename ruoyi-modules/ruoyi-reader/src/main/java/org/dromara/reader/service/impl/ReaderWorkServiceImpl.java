package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderComicPage;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderWorkBo;
import org.dromara.reader.domain.bo.ReaderCoverStyleBo;
import org.dromara.reader.domain.bo.ReaderWorkQueryBo;
import org.dromara.reader.domain.vo.ReaderWorkVo;
import org.dromara.reader.domain.vo.admin.ReaderCatalogAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderComicChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderNovelChapterAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderCoverStyleVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.job.ReaderPublishRefreshJob;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.service.IReaderWorkService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 阅读器作品管理服务实现，负责作品建档、目录查询、章节预览与上下架。
 */
@RequiredArgsConstructor
@Service
public class ReaderWorkServiceImpl implements IReaderWorkService {

    /**
     * 作品主表访问入口，负责管理端作品建档与状态流转。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 小说章节访问入口，负责目录查询与小说章节预览。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 小说章节正文访问入口，负责独立读取章节预览正文。
     */
    private final ReaderNovelChapterContentMapper novelChapterContentMapper;

    /**
     * 漫画章节访问入口，负责漫画目录查询与章节预览。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 漫画页访问入口，负责组装漫画章节预览图片列表。
     */
    private final ReaderComicPageMapper comicPageMapper;

    /**
     * 内容审核记录访问入口，负责读取作品当前最新审核状态。
     */
    private final ReaderContentAuditMapper contentAuditMapper;

    /**
     * 发布刷新任务，负责上下架后清理 C 端可见缓存。
     */
    private final ReaderPublishRefreshJob publishRefreshJob;

    /** 缺图作品的自动封面生成服务。 */
    private final ReaderCoverService readerCoverService;

    private final ReaderSourceChapterSnapshotMapper sourceChapterSnapshotMapper;

    /**
     * 创建作品草稿。
     */
    @Override
    public Long createWork(ReaderWorkBo bo) {
        ReaderWork work = MapstructUtils.convert(bo, ReaderWork.class);
        // 这里兜底初始化草稿作品的核心字段，避免管理端只填最小信息时产生脏数据。
        if (work.getPublishStatus() == null) {
            work.setPublishStatus(PublishStatus.DRAFT.name());
        }
        if (work.getSerialStatus() == null) {
            work.setSerialStatus("ONGOING");
        }
        if (work.getSourceType() == null) {
            work.setSourceType("IMPORT");
        }
        if (work.getAllowSearch() == null) {
            work.setAllowSearch("1");
        }
        if (work.getTotalChapters() == null) {
            work.setTotalChapters(0);
        }
        if (work.getTotalPages() == null) {
            work.setTotalPages(0);
        }
        if (work.getWorkType() == null) {
            work.setWorkType(WorkType.NOVEL.name());
        }
        if (work.getAuthorName() == null || work.getAuthorName().isBlank()) {
            work.setAuthorName("未知作者");
        }
        work.setAuthorName(work.getAuthorName().trim());
        work.setDedupeKey(buildDedupeKey(work.getTitle(), work.getAuthorName()));
        readerWorkMapper.insert(work);
        readerCoverService.ensureGeneratedCover(work);
        return work.getId();
    }

    /**
     * 按筛选条件分页查询列表数据。
     */
    @Override
    public PageResult<ReaderWorkVo> queryPageList(ReaderWorkQueryBo bo, PageQuery pageQuery) {
        Page<ReaderWorkVo> result = readerWorkMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        decorateAuditStatus(result.getRecords());
        return PageResult.build(result.getRecords(), result.getTotal());
    }

    /**
     * 查询作品详情。
     */
    @Override
    public ReaderWorkDetailAdminVo queryDetail(Long workId) {
        // 详情页这里改为显式组装 VO，避免遗漏映射声明时在运行期抛出转换器缺失异常。
        return buildWorkDetailVo(requireWork(workId));
    }

    /**
     * 查询作品目录。
     */
    @Override
    public List<ReaderCatalogAdminVo> queryCatalog(Long workId) {
        ReaderWork work = requireWork(workId);
        // 管理端目录需要不区分发布状态地看到全量草稿内容，方便审核和排障。
        if (WorkType.NOVEL.name().equals(work.getWorkType())) {
            return novelChapterMapper.selectList(Wrappers.<ReaderNovelChapter>lambdaQuery()
                    .eq(ReaderNovelChapter::getWorkId, workId)
                    .orderByAsc(ReaderNovelChapter::getChapterNo)
                    .orderByAsc(ReaderNovelChapter::getId))
                .stream()
                .map(chapter -> {
                    ReaderCatalogAdminVo vo = new ReaderCatalogAdminVo();
                    vo.setChapterId(chapter.getId());
                    vo.setChapterName(chapter.getChapterName());
                    vo.setChapterNo(chapter.getChapterNo());
                    vo.setVolumeName(chapter.getVolumeName());
                    vo.setWordCount(chapter.getWordCount());
                    vo.setPublishStatus(chapter.getPublishStatus());
                    vo.setUpdateTime(chapter.getUpdateTime());
                    return vo;
                })
                .toList();
        }
        return comicChapterMapper.selectList(Wrappers.<ReaderComicChapter>lambdaQuery()
                .eq(ReaderComicChapter::getWorkId, workId)
                .orderByAsc(ReaderComicChapter::getChapterNo)
                .orderByAsc(ReaderComicChapter::getId))
            .stream()
            .map(chapter -> {
                ReaderCatalogAdminVo vo = new ReaderCatalogAdminVo();
                vo.setChapterId(chapter.getId());
                vo.setChapterName(chapter.getChapterName());
                vo.setChapterNo(chapter.getChapterNo());
                vo.setPageCount(chapter.getPageCount());
                vo.setPublishStatus(chapter.getPublishStatus());
                vo.setUpdateTime(chapter.getUpdateTime());
                return vo;
            })
            .toList();
    }

    /**
     * 查询小说章节预览。
     */
    @Override
    public ReaderNovelChapterAdminVo queryNovelChapter(Long chapterId) {
        // 管理端预览正文时直接读取章节原文，不额外做发布状态过滤。
        ReaderNovelChapter chapter = novelChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new ServiceException("章节不存在");
        }
        ReaderNovelChapterAdminVo vo = new ReaderNovelChapterAdminVo();
        vo.setWorkId(chapter.getWorkId());
        vo.setChapterId(chapter.getId());
        vo.setVolumeName(chapter.getVolumeName());
        vo.setChapterName(chapter.getChapterName());
        vo.setChapterNo(chapter.getChapterNo());
        vo.setWordCount(chapter.getWordCount());
        vo.setPublishStatus(chapter.getPublishStatus());
        vo.setContent(resolveNovelChapterContent(chapter));
        return vo;
    }

    /**
     * 查询漫画章节预览。
     */
    @Override
    public ReaderComicChapterAdminVo queryComicChapter(Long chapterId) {
        // 漫画预览需要把页图列表一并返回，管理端才能在详情页直接验图。
        ReaderComicChapter chapter = comicChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new ServiceException("章节不存在");
        }
        ReaderComicChapterAdminVo vo = new ReaderComicChapterAdminVo();
        vo.setWorkId(chapter.getWorkId());
        vo.setChapterId(chapter.getId());
        vo.setChapterName(chapter.getChapterName());
        vo.setChapterNo(chapter.getChapterNo());
        vo.setPageCount(chapter.getPageCount());
        vo.setPublishStatus(chapter.getPublishStatus());
        vo.setImageUrls(comicPageMapper.selectList(Wrappers.<ReaderComicPage>lambdaQuery()
                .eq(ReaderComicPage::getChapterId, chapterId)
                .orderByAsc(ReaderComicPage::getPageNo))
            .stream()
            .map(ReaderComicPage::getImageUrl)
            .toList());
        return vo;
    }

    /**
     * 上架作品并同步章节状态。
     */
    @Override
    public void publish(Long workId) {
        ReaderWork work = requireWork(workId);
        // 上架前必须经过最新一次审核通过，避免未审或已驳回内容绕过审核流程直接对 C 端可见。
        requireApprovedAudit(workId);
        // 作品上架时要同步章节状态，否则 App 侧按章节过滤后会出现“作品可见但章节不可读”。
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        readerWorkMapper.updateById(work);
        novelChapterMapper.update(
            null,
            Wrappers.<ReaderNovelChapter>lambdaUpdate()
                .eq(ReaderNovelChapter::getWorkId, workId)
                .set(ReaderNovelChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
        );
        comicChapterMapper.update(
            null,
            Wrappers.<ReaderComicChapter>lambdaUpdate()
                .eq(ReaderComicChapter::getWorkId, workId)
                .set(ReaderComicChapter::getPublishStatus, PublishStatus.PUBLISHED.name())
        );
        publishRefreshJob.execute(workId);
    }

    /**
     * 下架作品并同步章节状态。
     */
    @Override
    public void offline(Long workId) {
        ReaderWork work = requireWork(workId);
        // 下架同样要同步章节状态，并刷新缓存，避免应用侧读到旧的发布态数据。
        work.setPublishStatus(PublishStatus.OFFLINE.name());
        readerWorkMapper.updateById(work);
        novelChapterMapper.update(
            null,
            Wrappers.<ReaderNovelChapter>lambdaUpdate()
                .eq(ReaderNovelChapter::getWorkId, workId)
                .set(ReaderNovelChapter::getPublishStatus, PublishStatus.OFFLINE.name())
        );
        comicChapterMapper.update(
            null,
            Wrappers.<ReaderComicChapter>lambdaUpdate()
                .eq(ReaderComicChapter::getWorkId, workId)
                .set(ReaderComicChapter::getPublishStatus, PublishStatus.OFFLINE.name())
        );
        publishRefreshJob.execute(workId);
    }

    @Override
    public int backfillMissingCovers() {
        return readerCoverService.backfillMissingCovers();
    }

    @Override
    public ReaderCoverStyleVo queryGlobalCoverStyle() {
        return readerCoverService.queryGlobalStyle();
    }

    @Override
    public int updateGlobalCoverStyle(ReaderCoverStyleBo bo) {
        return readerCoverService.updateGlobalStyle(bo);
    }

    @Override
    public void updateWorkCoverStyle(Long workId, ReaderCoverStyleBo bo) {
        readerCoverService.updateWorkStyle(requireWork(workId), bo);
        publishRefreshJob.execute(workId);
    }

    @Override
    public int reformatNovelContents() {
        int changed = 0;
        for (ReaderNovelChapterContent content : novelChapterContentMapper.selectList(Wrappers.lambdaQuery())) {
            String formatted = ReaderNovelTextFormatter.format(content.getContent());
            if (!formatted.equals(content.getContent())) {
                content.setContent(formatted);
                novelChapterContentMapper.updateById(content);
                changed++;
            }
        }
        sourceChapterSnapshotMapper.selectList(Wrappers.lambdaQuery()).forEach(snapshot -> {
            String formatted = ReaderNovelTextFormatter.format(snapshot.getContent());
            if (!formatted.equals(snapshot.getContent())) {
                snapshot.setContent(formatted);
                snapshot.setContentHash(sha256(formatted));
                sourceChapterSnapshotMapper.updateById(snapshot);
            }
        });
        return changed;
    }

    /**
     * 校验作品是否存在。
     */
    private ReaderWork requireWork(Long workId) {
        ReaderWork work = readerWorkMapper.selectById(workId);
        if (work == null) {
            throw new ServiceException("作品不存在");
        }
        return work;
    }

    /** 统一生成标题加作者的规范化 SHA-256 去重键。 */
    private String buildDedupeKey(String title, String author) {
        String normalizedTitle = normalizeText(title);
        String normalizedAuthor = normalizeText(author == null || author.isBlank() ? "未知作者" : author);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((normalizedTitle + "\u0000" + normalizedAuthor).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ServiceException("无法生成作品去重键");
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ServiceException("无法生成正文哈希");
        }
    }

    /**
     * 读取小说章节正文，并兼容迁移前旧表中的历史正文数据。
     */
    private String resolveNovelChapterContent(ReaderNovelChapter chapter) {
        ReaderNovelChapterContent content = novelChapterContentMapper.selectById(chapter.getId());
        if (content != null) {
            return content.getContent();
        }
        return novelChapterMapper.selectLegacyContentByChapterId(chapter.getId());
    }

    /**
     * 组装作品详情视图对象。
     */
    private ReaderWorkDetailAdminVo buildWorkDetailVo(ReaderWork work) {
        ReaderWorkDetailAdminVo vo = new ReaderWorkDetailAdminVo();
        vo.setId(work.getId());
        vo.setWorkType(work.getWorkType());
        vo.setCategoryName(work.getCategoryName());
        vo.setAuthorName(work.getAuthorName());
        vo.setTitle(work.getTitle());
        vo.setIntro(work.getIntro());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setCoverLandscapeUrl(work.getCoverLandscapeUrl());
        vo.setCoverBackgroundMode(StringUtils.isBlank(work.getCoverBackgroundMode()) ? "GLOBAL" : work.getCoverBackgroundMode());
        vo.setCoverBackgroundColor(work.getCoverBackgroundColor());
        vo.setCoverBackgroundOssId(work.getCoverBackgroundOssId());
        vo.setCoverBackgroundImageUrl(readerCoverService.resolveBackgroundImageUrl(work.getCoverBackgroundOssId()));
        vo.setCoverRevision(work.getCoverRevision());
        vo.setSerialStatus(work.getSerialStatus());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setSourceType(work.getSourceType());
        vo.setTotalChapters(work.getTotalChapters());
        vo.setTotalPages(work.getTotalPages());
        vo.setAllowSearch(work.getAllowSearch());
        vo.setCreateTime(work.getCreateTime());
        vo.setUpdateTime(work.getUpdateTime());
        // 详情页要明确展示当前审核门禁状态，方便运营理解为什么此时不能上架。
        vo.setAuditStatus(resolveLatestAuditStatus(work.getId()));
        return vo;
    }

    /**
     * 给作品列表补齐最新审核状态，避免管理端只能看到发布态却不知道是否具备上架资格。
     */
    private void decorateAuditStatus(List<ReaderWorkVo> works) {
        if (works == null || works.isEmpty()) {
            return;
        }
        Map<Long, String> latestAuditStatusMap = loadLatestAuditStatusMap(works.stream()
            .map(ReaderWorkVo::getId)
            .filter(id -> id != null)
            .toList());
        works.forEach(work -> work.setAuditStatus(latestAuditStatusMap.get(work.getId())));
    }

    /**
     * 读取指定作品集合的最新审核状态映射。
     */
    private Map<Long, String> loadLatestAuditStatusMap(List<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return Map.of();
        }
        return contentAuditMapper.selectList(Wrappers.<ReaderContentAudit>lambdaQuery()
                .in(ReaderContentAudit::getWorkId, workIds)
                .orderByDesc(ReaderContentAudit::getCreateTime)
                .orderByDesc(ReaderContentAudit::getId))
            .stream()
            // 同一作品只保留最新一条审核记录，后续上架门禁与界面展示都以这条记录为准。
            .collect(Collectors.toMap(
                ReaderContentAudit::getWorkId,
                ReaderContentAudit::getAuditStatus,
                (latest, ignored) -> latest
            ));
    }

    /**
     * 读取单个作品最新审核状态。
     */
    private String resolveLatestAuditStatus(Long workId) {
        return loadLatestAuditStatusMap(List.of(workId)).get(workId);
    }

    /**
     * 校验作品最新审核结果是否允许上架。
     */
    private void requireApprovedAudit(Long workId) {
        String auditStatus = resolveLatestAuditStatus(workId);
        if (!"APPROVED".equals(auditStatus)) {
            throw new ServiceException("作品未审核通过，禁止上架");
        }
    }

    /**
     * 构造作品列表查询条件。
     */
    private LambdaQueryWrapper<ReaderWork> buildQueryWrapper(ReaderWorkQueryBo bo) {
        LambdaQueryWrapper<ReaderWork> lqw = Wrappers.lambdaQuery();
        // 列表页筛选项都允许单独组合使用，所以这里按非空条件逐个拼装。
        lqw.like(StringUtils.isNotBlank(bo.getKeyword()), ReaderWork::getTitle, bo.getKeyword());
        lqw.eq(StringUtils.isNotBlank(bo.getWorkType()), ReaderWork::getWorkType, bo.getWorkType());
        lqw.eq(StringUtils.isNotBlank(bo.getPublishStatus()), ReaderWork::getPublishStatus, bo.getPublishStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceType()), ReaderWork::getSourceType, bo.getSourceType());
        lqw.orderByDesc(ReaderWork::getUpdateTime);
        lqw.orderByDesc(ReaderWork::getId);
        return lqw;
    }
}
