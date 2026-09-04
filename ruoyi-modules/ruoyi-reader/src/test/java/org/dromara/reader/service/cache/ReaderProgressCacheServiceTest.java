package org.dromara.reader.service.cache;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.reader.domain.ReaderReadingProgress;
import org.dromara.reader.mapper.ReaderReadingProgressMapper;
import org.dromara.reader.constant.ReaderConstants;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
public class ReaderProgressCacheServiceTest {

    @BeforeAll
    static void initMybatisPlusMetadata() {
        if (TableInfoHelper.getTableInfo(ReaderReadingProgress.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), ReaderReadingProgress.class.getName());
            TableInfoHelper.initTableInfo(assistant, ReaderReadingProgress.class);
        }
    }

    @Mock
    private ReaderReadingProgressMapper readingProgressMapper;
    @Mock
    private ReaderRedisCacheClient redisCacheClient;

    @InjectMocks
    private ReaderProgressCacheService service;

    @Test
    public void shouldBuildProgressCacheKeyPrefix() {
        assertEquals("reader:progress:", ReaderConstants.CACHE_READING_PROGRESS);
    }

    @Test
    public void shouldBuildDirtyProgressCacheKey() {
        assertEquals("reader:progress:dirty:7:12", service.progressDirtyKey(7L, 12L));
    }

    @Test
    public void cacheProgressShouldStoreProgressAndMarkDirty() {
        ReaderReadingProgress cached = new ReaderReadingProgress();
        cached.setUserId(7L);
        cached.setWorkId(12L);

        service.cacheProgress(cached);

        verify(redisCacheClient).setObject("reader:progress:7:12", cached);
        verify(redisCacheClient).setObject("reader:progress:dirty:7:12", Boolean.TRUE);
    }

    @Test
    public void flushProgressShouldPersistCachedProgressAndClearDirtyFlag() {
        ReaderReadingProgress cached = new ReaderReadingProgress();
        cached.setUserId(7L);
        cached.setWorkId(12L);
        cached.setChapterId(201L);
        cached.setLocationValue("8");
        cached.setProgressPercent(88);
        cached.setClientType("H5");
        cached.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 15, 0));

        ReaderReadingProgress existing = new ReaderReadingProgress();
        existing.setId(99L);
        existing.setUserId(7L);
        existing.setWorkId(12L);
        existing.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 14, 0));

        when(readingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(redisCacheClient.getObject("reader:progress:7:12")).thenReturn(cached);
        when(redisCacheClient.getObject("reader:progress:dirty:7:12")).thenReturn(Boolean.TRUE);

        boolean flushed = service.flushProgress(7L, 12L);

        assertTrue(flushed);
        verify(readingProgressMapper).updateById(argThat((ReaderReadingProgress progress) ->
            progress.getId().equals(99L)
                && progress.getChapterId().equals(201L)
                && progress.getProgressPercent().equals(88)
                && "8".equals(progress.getLocationValue())
                && "H5".equals(progress.getClientType())
        ));
        verify(redisCacheClient).deleteObject("reader:progress:dirty:7:12");
    }

    @Test
    public void flushProgressShouldRefreshCacheWhenDirtyProgressIsOlderThanMysql() {
        ReaderReadingProgress cached = new ReaderReadingProgress();
        cached.setUserId(7L);
        cached.setWorkId(12L);
        cached.setChapterId(201L);
        cached.setLocationValue("8");
        cached.setProgressPercent(88);
        cached.setClientType("H5");
        cached.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 14, 0));

        ReaderReadingProgress existing = new ReaderReadingProgress();
        existing.setId(99L);
        existing.setUserId(7L);
        existing.setWorkId(12L);
        existing.setChapterId(202L);
        existing.setLocationValue("9");
        existing.setProgressPercent(90);
        existing.setClientType("APP");
        existing.setProgressUpdatedAt(LocalDateTime.of(2026, 8, 10, 15, 0));

        when(readingProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(redisCacheClient.getObject("reader:progress:7:12")).thenReturn(cached);
        when(redisCacheClient.getObject("reader:progress:dirty:7:12")).thenReturn(Boolean.TRUE);

        boolean flushed = service.flushProgress(7L, 12L);

        assertEquals(false, flushed);
        verify(readingProgressMapper, never()).updateById(any(ReaderReadingProgress.class));
        verify(redisCacheClient).setObject("reader:progress:7:12", existing);
        verify(redisCacheClient).deleteObject("reader:progress:dirty:7:12");
    }
}
