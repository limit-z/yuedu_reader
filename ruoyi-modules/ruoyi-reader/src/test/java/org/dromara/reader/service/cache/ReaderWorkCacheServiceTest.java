package org.dromara.reader.service.cache;

import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ReaderAllEnvTest
@ExtendWith(MockitoExtension.class)
public class ReaderWorkCacheServiceTest {

    @Mock
    private ReaderRedisCacheClient redisCacheClient;

    @InjectMocks
    private ReaderWorkCacheService service;

    @Test
    public void evictPublishedWorkCachesShouldDeleteWorkDetailAndCatalogCaches() {
        service.evictPublishedWorkCaches(12L);

        verify(redisCacheClient).deleteObjects(List.of(
            "reader:work:detail:12",
            "reader:work:catalog:12"
        ));
    }

    @Test
    public void shouldBuildExpectedCacheKeysForChapterAndCatalogPage() {
        assertEquals("reader:work:catalog:page:9:2:20", service.catalogPageKey(9L, 2, 20));
        assertEquals("reader:work:chapter:novel:9:101", service.novelChapterKey(9L, 101L));
        assertEquals("reader:work:chapter:comic:9:102", service.comicChapterKey(9L, 102L));
    }

    @Test
    public void shouldWriteChapterAndPageCachesWithExpectedKeys() {
        var page = new org.dromara.reader.domain.vo.app.AppPageVo<org.dromara.reader.domain.vo.app.AppCatalogItemVo>();
        page.setPageNum(1);
        page.setPageSize(20);
        service.cacheCatalogPage(9L, 1, 20, page);

        var chapter = new org.dromara.reader.domain.vo.app.AppNovelChapterVo();
        chapter.setChapterId(101L);
        service.cacheNovelChapter(9L, chapter);

        verify(redisCacheClient).setObject(eq("reader:work:catalog:page:9:1:20"), eq(page), eq(java.time.Duration.ofMinutes(30)));
        verify(redisCacheClient).setObject(eq("reader:work:chapter:novel:9:101"), eq(chapter), eq(java.time.Duration.ofMinutes(30)));
    }
}
