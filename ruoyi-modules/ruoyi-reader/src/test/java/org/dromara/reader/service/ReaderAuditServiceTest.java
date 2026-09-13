package org.dromara.reader.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderAuditQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderAuditRecordVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.job.ReaderPublishRefreshJob;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.mapper.ReaderSourceTaskBookMapper;
import org.dromara.reader.mapper.ReaderSourceTaskMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.impl.ReaderAuditServiceImpl;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
public class ReaderAuditServiceTest {

    @Mock
    private ReaderContentAuditMapper contentAuditMapper;
    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderComicChapterMapper comicChapterMapper;
    @Mock
    private ReaderPublishRefreshJob publishRefreshJob;
    @Mock
    private ReaderSourceChapterSnapshotMapper snapshotMapper;
    @Mock
    private ReaderSourceTaskMapper sourceTaskMapper;
    @Mock
    private ReaderSourceTaskBookMapper sourceTaskBookMapper;

    @InjectMocks
    private ReaderAuditServiceImpl service;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderContentAudit.class);
        initializeTableInfo(ReaderNovelChapter.class);
        initializeTableInfo(ReaderComicChapter.class);
    }

    @Test
    public void publishedStateShouldBeAvailableForApprovedContent() {
        assertEquals("PUBLISHED", PublishStatus.PUBLISHED.name());
    }

    @Test
    public void approveShouldRefreshPublishedWorkCaches() {
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setId(3L);
        audit.setWorkId(12L);
        ReaderWork work = new ReaderWork();
        work.setId(12L);
        work.setPublishStatus("DRAFT");

        when(contentAuditMapper.selectById(3L)).thenReturn(audit);
        when(readerWorkMapper.selectById(12L)).thenReturn(work);

        service.approve(3L);

        verify(novelChapterMapper).update(isNull(), any());
        verify(comicChapterMapper).update(isNull(), any());
        verify(publishRefreshJob).execute(12L);
    }

    @Test
    public void queryPageListShouldDecorateWorkTitle() {
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setId(8L);
        audit.setWorkId(12L);
        audit.setAuditStatus("PENDING");
        Page<ReaderContentAudit> page = new Page<>(1, 10);
        page.setRecords(List.of(audit));
        page.setTotal(1);

        ReaderWork work = new ReaderWork();
        work.setId(12L);
        work.setTitle("三体");

        when(contentAuditMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(readerWorkMapper.selectBatchIds(List.of(12L))).thenReturn(List.of(work));

        PageResult<ReaderAuditRecordVo> result = service.queryPageList(new ReaderAuditQueryBo(), new PageQuery(10, 1));

        assertEquals(1, result.getTotal());
        assertEquals("三体", result.getRows().iterator().next().getWorkTitle());
    }

    @Test
    public void queryPageListShouldReturnEmptyRowsWithoutQueryingWorkTitlesWhenAuditPageIsEmpty() {
        Page<ReaderContentAudit> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);

        when(contentAuditMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ReaderAuditRecordVo> result = service.queryPageList(new ReaderAuditQueryBo(), new PageQuery(10, 1));

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getRows().size());
        verify(readerWorkMapper, never()).selectBatchIds(any());
    }

    private static void initializeTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
