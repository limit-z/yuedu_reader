package org.dromara.reader.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.ReaderContentAudit;
import org.dromara.reader.domain.ReaderComicChapter;
import org.dromara.reader.domain.ReaderNovelChapter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.reader.mapper.ReaderNovelChapterContentMapper;
import org.dromara.reader.mapper.ReaderComicChapterMapper;
import org.dromara.reader.mapper.ReaderComicPageMapper;
import org.dromara.reader.mapper.ReaderContentAuditMapper;
import org.dromara.reader.mapper.ReaderNovelChapterMapper;
import org.dromara.reader.domain.vo.admin.ReaderWorkDetailAdminVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.job.ReaderPublishRefreshJob;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.mapper.ReaderSourceChapterSnapshotMapper;
import org.dromara.reader.service.impl.ReaderWorkServiceImpl;
import org.dromara.reader.service.impl.ReaderCoverService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
class ReaderWorkServiceTest {

    @Mock
    private ReaderWorkMapper readerWorkMapper;
    @Mock
    private ReaderNovelChapterMapper novelChapterMapper;
    @Mock
    private ReaderNovelChapterContentMapper novelChapterContentMapper;
    @Mock
    private ReaderComicChapterMapper comicChapterMapper;
    @Mock
    private ReaderComicPageMapper comicPageMapper;
    @Mock
    private ReaderContentAuditMapper contentAuditMapper;
    @Mock
    private ReaderPublishRefreshJob publishRefreshJob;
    @Mock
    private ReaderCoverService readerCoverService;
    @Mock
    private ReaderSourceChapterSnapshotMapper sourceChapterSnapshotMapper;

    @InjectMocks
    private ReaderWorkServiceImpl service;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        initializeTableInfo(ReaderContentAudit.class);
        initializeTableInfo(ReaderNovelChapter.class);
        initializeTableInfo(ReaderComicChapter.class);
    }

    @Test
    void publishShouldRefreshPublishedWorkCaches() {
        ReaderWork work = new ReaderWork();
        work.setId(7L);
        work.setPublishStatus(PublishStatus.DRAFT.name());
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setWorkId(7L);
        audit.setAuditStatus("APPROVED");
        when(readerWorkMapper.selectById(7L)).thenReturn(work);
        when(contentAuditMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(audit));

        service.publish(7L);

        verify(readerWorkMapper).updateById(work);
        verify(novelChapterMapper).update(org.mockito.Mockito.isNull(), org.mockito.ArgumentMatchers.any());
        verify(comicChapterMapper).update(org.mockito.Mockito.isNull(), org.mockito.ArgumentMatchers.any());
        verify(publishRefreshJob).execute(7L);
    }

    @Test
    void publishShouldRejectWorkWhenLatestAuditIsNotApproved() {
        ReaderWork work = new ReaderWork();
        work.setId(7L);
        work.setPublishStatus(PublishStatus.DRAFT.name());
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setWorkId(7L);
        audit.setAuditStatus("PENDING");
        when(readerWorkMapper.selectById(7L)).thenReturn(work);
        when(contentAuditMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(audit));

        org.dromara.common.core.exception.ServiceException exception = assertThrows(
            org.dromara.common.core.exception.ServiceException.class,
            () -> service.publish(7L)
        );

        assertEquals("作品未审核通过，禁止上架", exception.getMessage());
    }

    @Test
    void offlineShouldRefreshPublishedWorkCaches() {
        ReaderWork work = new ReaderWork();
        work.setId(7L);
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        when(readerWorkMapper.selectById(7L)).thenReturn(work);

        service.offline(7L);

        verify(readerWorkMapper).updateById(work);
        verify(novelChapterMapper).update(org.mockito.Mockito.isNull(), org.mockito.ArgumentMatchers.any());
        verify(comicChapterMapper).update(org.mockito.Mockito.isNull(), org.mockito.ArgumentMatchers.any());
        verify(publishRefreshJob).execute(7L);
    }

    @Test
    void queryDetailShouldAssembleAdminDetailVo() {
        ReaderWork work = new ReaderWork();
        work.setId(11L);
        work.setWorkType("NOVEL");
        work.setTitle("三体");
        work.setIntro("科幻作品");
        work.setPublishStatus(PublishStatus.PUBLISHED.name());
        work.setSourceType("IMPORT");
        work.setAllowSearch("1");
        work.setTotalChapters(120);
        ReaderContentAudit audit = new ReaderContentAudit();
        audit.setWorkId(11L);
        audit.setAuditStatus("APPROVED");
        when(readerWorkMapper.selectById(11L)).thenReturn(work);
        when(contentAuditMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(audit));

        ReaderWorkDetailAdminVo detailVo = service.queryDetail(11L);

        assertEquals(11L, detailVo.getId());
        assertEquals("NOVEL", detailVo.getWorkType());
        assertEquals("三体", detailVo.getTitle());
        assertEquals("科幻作品", detailVo.getIntro());
        assertEquals(PublishStatus.PUBLISHED.name(), detailVo.getPublishStatus());
        assertEquals("IMPORT", detailVo.getSourceType());
        assertEquals("1", detailVo.getAllowSearch());
        assertEquals(120, detailVo.getTotalChapters());
        assertEquals("APPROVED", detailVo.getAuditStatus());
    }

    private static void initializeTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
