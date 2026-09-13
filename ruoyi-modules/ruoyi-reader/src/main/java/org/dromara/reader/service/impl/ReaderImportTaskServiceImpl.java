package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderComicPage;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderImportFile;
import org.dromara.reader.domain.ReaderImportTask;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderNovelChapterContent;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderImportTaskBo;
import org.dromara.reader.domain.bo.ReaderImportTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderImportTaskAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;
import org.dromara.reader.enums.ImportTaskStatus;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.ReadingContentType;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderImportFileMapper;
import org.dromara.reader.mapper.ReaderImportTaskMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderImportTaskService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;

/**
 * 阅读器导入任务服务实现，负责把上传文件落成草稿作品、章节与待审记录。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReaderImportTaskServiceImpl implements IReaderImportTaskService {

    /**
     * 导入失败原因在任务表中的最大保留长度，避免异常堆栈撑爆展示字段。
     */
    private static final int MAX_FAIL_REASON_LENGTH = 1000;

    /**
     * 小说文本导入时按优先级尝试的字符集列表，兼容 UTF-8 与常见国标编码。
     */
    private static final List<Charset> TEXT_CHARSETS = List.of(
        StandardCharsets.UTF_8,
        Charset.forName("GB18030"),
        Charset.forName("GBK")
    );

    /**
     * 常见中文网文章节标题规则，覆盖“第xx章/卷/回/节”这类主流写法。
     */
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile(
        "^(第[0-9零一二三四五六七八九十百千万两〇○]+[章节卷集部篇回话].*|"
            + "(序章|楔子|前言|引子|正文|尾声|后记|番外.*|终章.*))$"
    );

    /**
     * 小说章节批量入库批次大小，平衡单次 SQL 体积与往返次数。
     */
    private static final int NOVEL_CHAPTER_BATCH_SIZE = 200;

    /**
     * 漫画分页批量入库批次大小，避免页数较多时逐条写入拖慢导入。
     */
    private static final int COMIC_PAGE_BATCH_SIZE = 200;

    /**
     * 导入任务主表访问入口，负责任务状态与失败原因持久化。
     */
    private final ReaderImportTaskMapper importTaskMapper;

    /**
     * 导入文件记录访问入口，负责保存原始文件与任务的关联关系。
     */
    private final ReaderImportFileMapper importFileMapper;

    /**
     * 作品主表访问入口，负责落草稿作品与更新章节统计。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 小说章节访问入口，负责把文本导入结果沉淀成章节草稿。
     */
    private final ReaderNovelChapterMapper novelChapterMapper;

    /**
     * 小说章节正文访问入口，负责把正文大字段拆到独立存储表。
     */
    private final ReaderNovelChapterContentMapper novelChapterContentMapper;

    /**
     * 漫画章节访问入口，负责创建漫画章节草稿。
     */
    private final ReaderComicChapterMapper comicChapterMapper;

    /**
     * 漫画页访问入口，负责按图片顺序保存页数据。
     */
    private final ReaderComicPageMapper comicPageMapper;

    /**
     * 内容审核记录访问入口，负责把导入作品送入审核池。
     */
    private final ReaderContentAuditMapper contentAuditMapper;

    /**
     * OSS 访问服务，负责回源读取上传后的导入文件。
     */
    private final ISysOssService ossService;

    /**
     * 导入事务模板，确保作品、章节、审核记录按一次事务一致落库。
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * 全局后台任务线程池，负责把大文件导入从请求线程切到后台执行。
     */
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * 创建导入任务并立即触发文件解析。
     */
    @Override
    public Long createTask(ReaderImportTaskBo bo) {
        // 导入任务必须依赖一次已经完成的 OSS 上传，这样解析阶段才能稳定复用统一的文件访问链路。
        if (bo.getOssId() == null) {
            throw new ServiceException("请先上传导入文件");
        }
        ReaderImportTask task = new ReaderImportTask();
        task.setId(bo.getId());
        task.setTaskName(bo.getTaskName());
        task.setContentType(bo.getContentType());
        task.setCategoryName(bo.getCategoryName());
        task.setOssId(bo.getOssId());
        task.setCoverOssId(bo.getCoverOssId());
        if (task.getContentType() == null) {
            task.setContentType(ReadingContentType.NOVEL.name());
        }
        task.setStatus(ImportTaskStatus.CREATED.name());
        task.setTotalUnits(0);
        task.setProcessedUnits(0);
        task.setProgressPercent(0);
        task.setProgressMessage("任务已创建，等待后台解析");
        importTaskMapper.insert(task);
        // 大文件导入改为后台异步解析，避免请求线程长时间阻塞到前端超时。
        scheduledExecutorService.execute(() -> processImportTask(task));
        return task.getId();
    }

    /**
     * 按筛选条件分页查询列表数据。
     */
    @Override
    public PageResult<ReaderImportTaskAdminVo> queryPageList(ReaderImportTaskQueryBo bo, PageQuery pageQuery) {
        Page<ReaderImportTask> page = importTaskMapper.selectPage(pageQuery.build(), buildQueryWrapper(bo));
        List<ReaderImportTaskAdminVo> rows = page.getRecords().stream().map(this::buildAdminVo).toList();
        return PageResult.build(rows, page.getTotal());
    }

    /**
     * 批量取消或重试解析任务，逐条校验状态并保留失败原因。
     */
    @Override
    public ReaderBatchActionResult batchAction(List<Long> taskIds, String action) {
        return ReaderBatchActionResult.execute(taskIds, taskId -> {
            ReaderImportTask task = importTaskMapper.selectById(taskId);
            if (task == null) {
                return "导入任务不存在";
            }
            if ("cancel".equals(action)) {
                if (ImportTaskStatus.CANCELED.name().equals(task.getStatus())) {
                    return "任务已经取消";
                }
                if (ImportTaskStatus.PENDING_REVIEW.name().equals(task.getStatus())
                    || ImportTaskStatus.COMPLETED.name().equals(task.getStatus())) {
                    return "任务已完成，不能取消";
                }
                ReaderImportTask canceled = new ReaderImportTask();
                canceled.setId(taskId);
                canceled.setStatus(ImportTaskStatus.CANCELED.name());
                canceled.setProgressMessage("已批量取消");
                canceled.setFailReason(null);
                importTaskMapper.updateById(canceled);
                return null;
            }
            if (!ImportTaskStatus.PARSE_FAILED.name().equals(task.getStatus())
                && !ImportTaskStatus.CANCELED.name().equals(task.getStatus())) {
                return "仅解析失败或已取消的任务可以重试";
            }
            ReaderImportTask retryTask = new ReaderImportTask();
            retryTask.setId(taskId);
            retryTask.setStatus(ImportTaskStatus.CREATED.name());
            retryTask.setFailReason(null);
            retryTask.setProgressPercent(0);
            retryTask.setProcessedUnits(0);
            retryTask.setProgressMessage("已批量加入解析队列");
            importTaskMapper.updateById(retryTask);
            task.setStatus(ImportTaskStatus.CREATED.name());
            task.setFailReason(null);
            task.setProgressPercent(0);
            task.setProcessedUnits(0);
            task.setProgressMessage("已批量加入解析队列");
            scheduledExecutorService.execute(() -> processImportTask(task));
            return null;
        });
    }

    /**
     * 构造导入任务分页查询条件。
     */
    private LambdaQueryWrapper<ReaderImportTask> buildQueryWrapper(ReaderImportTaskQueryBo bo) {
        LambdaQueryWrapper<ReaderImportTask> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getTaskName()), ReaderImportTask::getTaskName, bo.getTaskName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), ReaderImportTask::getStatus, bo.getStatus());
        lqw.orderByDesc(ReaderImportTask::getCreateTime);
        lqw.orderByDesc(ReaderImportTask::getId);
        return lqw;
    }

    /**
     * 组装导入任务管理端视图对象。
     */
    private ReaderImportTaskAdminVo buildAdminVo(ReaderImportTask task) {
        ReaderImportTaskAdminVo vo = new ReaderImportTaskAdminVo();
        vo.setId(task.getId());
        vo.setTaskName(task.getTaskName());
        vo.setContentType(task.getContentType());
        vo.setCategoryName(task.getCategoryName());
        vo.setStatus(task.getStatus());
        vo.setTotalUnits(task.getTotalUnits());
        vo.setProcessedUnits(task.getProcessedUnits());
        vo.setProgressPercent(task.getProgressPercent());
        vo.setProgressMessage(task.getProgressMessage());
        vo.setFailReason(task.getFailReason());
        vo.setCoverOssId(task.getCoverOssId());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    /**
     * 处理导入文件并生成草稿作品。
     */
    private void handleImportedFile(ReaderImportTask task) {
        // 导入链路会先生成草稿作品，再按内容类型落章节，最后补一条待审记录进入审核池。
        SysOssVo oss = ossService.getById(task.getOssId());
        if (oss == null) {
            throw new ServiceException("导入文件不存在");
        }
        ReaderImportFile importFile = new ReaderImportFile();
        importFile.setTaskId(task.getId());
        importFile.setOriginName(oss.getOriginalName());
        importFile.setFileSuffix(resolveSuffix(oss.getOriginalName(), oss.getFileSuffix()));
        importFile.setOssId(oss.getOssId());
        importFileMapper.insert(importFile);

        // 任务名称优先作为作品标题，没填时再回退到原始文件名，方便管理端快速识别。
        String title = resolveTitle(task.getTaskName(), oss.getOriginalName());
        ReaderWork work = buildDraftWork(task, title);
        readerWorkMapper.insert(work);

        if (WorkType.COMIC.name().equals(task.getContentType())) {
            buildComicDraft(task.getId(), work.getId(), oss);
        } else {
            buildNovelDraft(task.getId(), work.getId(), oss);
        }

        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setWorkId(work.getId());
        audit.setAuditStatus("PENDING");
        contentAuditMapper.insert(audit);
    }

    /**
     * 构造导入后生成的草稿作品。
     */
    private ReaderWork buildDraftWork(ReaderImportTask task, String title) {
        ReaderWork work = new ReaderWork();
        work.setWorkType(task.getContentType());
        work.setCategoryName(task.getCategoryName());
        work.setTitle(title);
        work.setAuthorName("未知作者");
        work.setDedupeKey(buildDedupeKey(title, work.getAuthorName()));
        work.setIntro("由文件导入生成的草稿内容");
        work.setCoverUrl(resolveCoverUrl(task.getCoverOssId()));
        work.setPublishStatus(PublishStatus.DRAFT.name());
        work.setSerialStatus("ONGOING");
        work.setSourceType("IMPORT");
        work.setAllowSearch("1");
        work.setTotalChapters(0);
        work.setTotalPages(0);
        return work;
    }

    private String buildDedupeKey(String title, String author) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = normalizeDedupeText(title) + "\u0000" + normalizeDedupeText(author);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成作品去重键", ex);
        }
    }

    private String normalizeDedupeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 读取管理端上传的作品封面地址；未上传封面时保留空值，由客户端使用默认封面。
     */
    private String resolveCoverUrl(Long coverOssId) {
        if (coverOssId == null) {
            return null;
        }
        SysOssVo cover = ossService.getById(coverOssId);
        return cover == null ? null : cover.getUrl();
    }

    /**
     * 基于导入文件生成小说草稿章节。
     */
    private void buildNovelDraft(Long taskId, Long workId, SysOssVo oss) {
        // 小说导入会先按常见中文网文章节标题做粗分章，保证目录和阅读页能一章一章工作。
        String text = loadTextContent(oss);
        List<ParsedNovelChapter> chapters = splitNovelChapters(text);
        updateTaskProgress(taskId, ImportTaskStatus.PARSING, chapters.size(), 0, "已完成分章，开始批量写入章节");
        List<ReaderNovelChapter> chapterEntities = new ArrayList<>(chapters.size());
        List<ReaderNovelChapterContent> contentEntities = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            ParsedNovelChapter parsedChapter = chapters.get(i);
            ReaderNovelChapter chapter = new ReaderNovelChapter();
            Long chapterId = IdWorker.getId();
            chapter.setId(chapterId);
            chapter.setWorkId(workId);
            chapter.setChapterName(parsedChapter.chapterName());
            chapter.setChapterNo(i + 1);
            chapter.setWordCount(parsedChapter.content().length());
            chapter.setPublishStatus(PublishStatus.DRAFT.name());
            chapterEntities.add(chapter);

            ReaderNovelChapterContent contentEntity = new ReaderNovelChapterContent();
            contentEntity.setChapterId(chapterId);
            contentEntity.setContent(parsedChapter.content());
            contentEntities.add(contentEntity);
        }
        // 章节按批次落库，并在每批完成后回写任务进度，方便后台看到大书导入推进情况。
        int processedUnits = 0;
        for (int start = 0; start < chapterEntities.size(); start += NOVEL_CHAPTER_BATCH_SIZE) {
            int end = Math.min(start + NOVEL_CHAPTER_BATCH_SIZE, chapterEntities.size());
            novelChapterMapper.insertBatch(new ArrayList<>(chapterEntities.subList(start, end)), NOVEL_CHAPTER_BATCH_SIZE);
            novelChapterContentMapper.insertBatch(new ArrayList<>(contentEntities.subList(start, end)), NOVEL_CHAPTER_BATCH_SIZE);
            processedUnits = end;
            updateTaskProgress(taskId, ImportTaskStatus.PARSING, chapterEntities.size(), processedUnits,
                "已写入章节 " + processedUnits + "/" + chapterEntities.size());
        }

        ReaderWork work = new ReaderWork();
        work.setId(workId);
        work.setTotalChapters(chapters.size());
        readerWorkMapper.updateById(work);
    }

    /**
     * 基于导入文件生成漫画草稿章节。
     */
    private void buildComicDraft(Long taskId, Long workId, SysOssVo oss) {
        // 漫画导入会把压缩包里的图片顺序映射成页序，先保证可以预览和阅读。
        List<String> imageUrls = loadComicImageUrls(oss);
        updateTaskProgress(taskId, ImportTaskStatus.PARSING, imageUrls.size(), 0, "已识别漫画页，开始生成草稿");
        ReaderComicChapter chapter = new ReaderComicChapter();
        chapter.setWorkId(workId);
        chapter.setChapterName("第1话");
        chapter.setChapterNo(1);
        chapter.setPageCount(imageUrls.size());
        chapter.setPublishStatus(PublishStatus.DRAFT.name());
        comicChapterMapper.insert(chapter);

        List<ReaderComicPage> pageEntities = new ArrayList<>(imageUrls.size());
        for (int i = 0; i < imageUrls.size(); i++) {
            ReaderComicPage page = new ReaderComicPage();
            page.setChapterId(chapter.getId());
            page.setPageNo(i + 1);
            page.setImageUrl(imageUrls.get(i));
            pageEntities.add(page);
        }

        // 漫画分页也按批次写入，避免大图包逐页 insert 导致导入任务拖得过久。
        int processedUnits = 0;
        for (int start = 0; start < pageEntities.size(); start += COMIC_PAGE_BATCH_SIZE) {
            int end = Math.min(start + COMIC_PAGE_BATCH_SIZE, pageEntities.size());
            comicPageMapper.insertBatch(new ArrayList<>(pageEntities.subList(start, end)), COMIC_PAGE_BATCH_SIZE);
            processedUnits = end;
            updateTaskProgress(taskId, ImportTaskStatus.PARSING, imageUrls.size(), processedUnits,
                "已写入漫画页 " + processedUnits + "/" + imageUrls.size());
        }

        ReaderWork work = readerWorkMapper.selectById(workId);
        if (work != null) {
            work.setTotalChapters(1);
            work.setTotalPages(imageUrls.size());
            readerWorkMapper.updateById(work);
        }
    }

    /**
     * 读取小说导入文本内容。
     */
    private String loadTextContent(SysOssVo oss) {
        // 小说导入优先识别常见文本或压缩电子书格式，失败时再回退到直接文本解码。
        byte[] bytes = downloadBytes(oss.getOssId());
        String suffix = resolveSuffix(oss.getOriginalName(), oss.getFileSuffix());
        if ("txt".equalsIgnoreCase(suffix)) {
            return decodeText(bytes);
        }
        if ("epub".equalsIgnoreCase(suffix) || "zip".equalsIgnoreCase(suffix) || "cbz".equalsIgnoreCase(suffix)) {
            return extractZipText(bytes);
        }
        return decodeText(bytes);
    }

    /**
     * 读取漫画导入图片地址列表。
     */
    private List<String> loadComicImageUrls(SysOssVo oss) {
        // 漫画最小闭环允许单图直传，也允许 zip/cbz 里包含多页图片。
        String suffix = resolveSuffix(oss.getOriginalName(), oss.getFileSuffix());
        if (!"zip".equalsIgnoreCase(suffix) && !"cbz".equalsIgnoreCase(suffix)) {
            return List.of(oss.getUrl());
        }
        List<String> imageUrls = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(downloadBytes(oss.getOssId())))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String lowerName = entry.getName().toLowerCase(Locale.ROOT);
                if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                    || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")) {
                    imageUrls.add(oss.getUrl() + "#" + entry.getName());
                }
            }
        } catch (IOException ignored) {
            return List.of(oss.getUrl());
        }
        return imageUrls.isEmpty() ? List.of(oss.getUrl()) : imageUrls;
    }

    /**
     * 从压缩包中提取可读文本内容。
     */
    private String extractZipText(byte[] bytes) {
        // zip/epub 里只提取可阅读文本，脚本和样式内容会在这里被过滤掉。
        StringBuilder builder = new StringBuilder();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String lowerName = entry.getName().toLowerCase(Locale.ROOT);
                if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".html") && !lowerName.endsWith(".htm")
                    && !lowerName.endsWith(".xhtml")) {
                    continue;
                }
                builder.append(stripHtml(decodeText(zipInputStream.readAllBytes()))).append("\n\n");
            }
        } catch (IOException ignored) {
            return decodeText(bytes);
        }
        return builder.length() == 0 ? decodeText(bytes) : builder.toString();
    }

    /**
     * 下载 OSS 文件二进制内容。
     */
    private byte[] downloadBytes(Long ossId) {
        ResponseEntity<byte[]> response = ossService.download(ossId);
        if (response == null || response.getBody() == null) {
            throw new ServiceException("导入文件下载失败");
        }
        return response.getBody();
    }

    /**
     * 去除 HTML 标签并保留可读正文。
     */
    private String stripHtml(String content) {
        return content.replaceAll("(?is)<script.*?>.*?</script>", "")
            .replaceAll("(?is)<style.*?>.*?</style>", "")
            .replaceAll("(?s)<[^>]+>", "")
            .replace("&nbsp;", " ")
            .trim();
    }

    /**
     * 按候选编码顺序解码文本。
     */
    private String decodeText(byte[] bytes) {
        // 导入文件编码来源复杂，按 UTF-8/GB18030/GBK 依次探测能明显降低乱码概率。
        for (Charset charset : TEXT_CHARSETS) {
            try {
                return trimBom(charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
            } catch (CharacterCodingException ignored) {
                // Continue probing the next charset candidate.
            }
        }
        return trimBom(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 清理文本头部 BOM 标记。
     */
    private String trimBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\ufeff') {
            return content.substring(1);
        }
        return content;
    }

    /**
     * 更新导入任务状态。
     */
    private void updateTaskStatus(Long taskId, ImportTaskStatus status, String failReason) {
        ReaderImportTask task = new ReaderImportTask();
        task.setId(taskId);
        task.setStatus(status.name());
        task.setFailReason(failReason);
        importTaskMapper.updateById(task);
    }

    /**
     * 生成适合管理端展示的失败原因。
     */
    private String buildFailReason(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = StringUtils.defaultIfBlank(current.getMessage(), ex.getMessage());
        if (StringUtils.isBlank(message)) {
            message = "导入文件解析失败";
        }
        return StringUtils.substring(message, 0, MAX_FAIL_REASON_LENGTH);
    }

    /**
     * 更新导入任务处理进度。
     */
    private void updateTaskProgress(Long taskId, ImportTaskStatus status, int totalUnits, int processedUnits, String progressMessage) {
        ReaderImportTask task = new ReaderImportTask();
        task.setId(taskId);
        task.setStatus(status.name());
        task.setTotalUnits(totalUnits);
        task.setProcessedUnits(processedUnits);
        task.setProgressPercent(resolveProgressPercent(totalUnits, processedUnits));
        task.setProgressMessage(progressMessage);
        importTaskMapper.updateById(task);
    }

    /**
     * 解析文件后缀。
     */
    private String resolveSuffix(String originalName, String fileSuffix) {
        if (StringUtils.isNotBlank(fileSuffix)) {
            return fileSuffix.startsWith(".") ? fileSuffix.substring(1) : fileSuffix;
        }
        if (StringUtils.isBlank(originalName) || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1);
    }

    /**
     * 解析作品标题。
     */
    private String resolveTitle(String taskName, String originalName) {
        if (StringUtils.isNotBlank(taskName)) {
            return taskName;
        }
        if (StringUtils.isBlank(originalName) || !originalName.contains(".")) {
            return originalName;
        }
        return originalName.substring(0, originalName.lastIndexOf('.'));
    }

    /**
     * 后台执行导入任务解析。
     */
    private void processImportTask(ReaderImportTask task) {
        if (isCanceled(task.getId())) {
            return;
        }
        updateTaskProgress(task.getId(), ImportTaskStatus.PARSING, 0, 0, "开始解析导入文件");
        try {
            transactionTemplate.executeWithoutResult(status -> handleImportedFile(task));
            if (isCanceled(task.getId())) {
                return;
            }
            ReaderImportTask finalTask = new ReaderImportTask();
            finalTask.setId(task.getId());
            finalTask.setStatus(ImportTaskStatus.PENDING_REVIEW.name());
            finalTask.setProgressPercent(100);
            finalTask.setProgressMessage("导入完成，等待审核");
            importTaskMapper.updateById(finalTask);
        } catch (Exception ex) {
            if (isCanceled(task.getId())) {
                return;
            }
            String failReason = buildFailReason(ex);
            log.warn("导入任务处理失败, taskId={}, reason={}", task.getId(), failReason, ex);
            ReaderImportTask failedTask = new ReaderImportTask();
            failedTask.setId(task.getId());
            failedTask.setStatus(ImportTaskStatus.PARSE_FAILED.name());
            failedTask.setFailReason(failReason);
            failedTask.setProgressMessage("导入失败");
            importTaskMapper.updateById(failedTask);
        }
    }

    /**
     * 异步解析完成前检查任务是否已被管理端取消，避免取消后又被后台线程覆盖状态。
     */
    private boolean isCanceled(Long taskId) {
        ReaderImportTask current = importTaskMapper.selectById(taskId);
        return current != null && Objects.equals(current.getStatus(), ImportTaskStatus.CANCELED.name());
    }

    /**
     * 计算任务进度百分比。
     */
    private int resolveProgressPercent(int totalUnits, int processedUnits) {
        if (totalUnits <= 0) {
            return 0;
        }
        if (processedUnits <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round(processedUnits * 100.0 / totalUnits));
    }

    /**
     * 按常见章节标题规则切分小说正文。
     */
    private List<ParsedNovelChapter> splitNovelChapters(String text) {
        String normalizedText = normalizeNovelText(text);
        if (StringUtils.isBlank(normalizedText)) {
            return List.of(new ParsedNovelChapter("第1章", "导入内容为空，当前为占位草稿。"));
        }

        List<ParsedNovelChapter> chapters = new ArrayList<>();
        String currentChapterName = null;
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : normalizedText.split("\n")) {
            String line = rawLine.strip();
            if (isChapterTitle(line)) {
                if (currentChapterName == null && currentContent.length() > 0) {
                    chapters.add(buildParsedChapter("前言", currentContent));
                } else if (currentChapterName != null) {
                    chapters.add(buildParsedChapter(currentChapterName, currentContent));
                }
                currentChapterName = line;
                currentContent = new StringBuilder();
                continue;
            }

            if (currentContent.length() > 0) {
                currentContent.append('\n');
            }
            currentContent.append(rawLine.stripTrailing());
        }

        if (currentChapterName != null) {
            chapters.add(buildParsedChapter(currentChapterName, currentContent));
        } else if (currentContent.length() > 0) {
            chapters.add(buildParsedChapter("第1章", currentContent));
        }

        return chapters.isEmpty() ? List.of(new ParsedNovelChapter("第1章", normalizedText)) : chapters;
    }

    /**
     * 规范化小说正文换行与头尾空白，降低不同来源文本的分章偏差。
     */
    private String normalizeNovelText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
    }

    /**
     * 判断一行文本是否可视为章节标题。
     */
    private boolean isChapterTitle(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        return CHAPTER_TITLE_PATTERN.matcher(line).matches();
    }

    /**
     * 构造分章结果并兜底空内容。
     */
    private ParsedNovelChapter buildParsedChapter(String chapterName, StringBuilder contentBuilder) {
        String content = contentBuilder == null ? "" : contentBuilder.toString().trim();
        if (StringUtils.isBlank(content)) {
            content = "本章暂无正文内容，当前为导入占位草稿。";
        }
        return new ParsedNovelChapter(chapterName, content);
    }

    /**
     * 小说分章中间结果。
     */
    private record ParsedNovelChapter(String chapterName, String content) {
    }
}
