package org.dromara.reader.constant;

/**
 * 阅读器模块常量定义，统一维护模块内可复用的固定值。
 */
public interface ReaderConstants {

    String CACHE_WORK_DETAIL = "reader:work:detail:";
    String CACHE_WORK_CATALOG = "reader:work:catalog:";
    String CACHE_WORK_CATALOG_PAGE = "reader:work:catalog:page:";
    String CACHE_NOVEL_CHAPTER = "reader:work:chapter:novel:";
    String CACHE_COMIC_CHAPTER = "reader:work:chapter:comic:";
    String CACHE_READING_PROGRESS = "reader:progress:";
    String VISITOR_ID_HEADER = "X-Reader-Visitor-Id";
    String VISITOR_ID_PARAM = "readerVisitorId";
}
