package org.dromara.reader.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.reader.domain.ReaderNovelChapter;

/**
 * 小说章节元数据访问层，负责目录、序号、字数等轻量字段查询。
 */
public interface ReaderNovelChapterMapper extends BaseMapperPlus<ReaderNovelChapter, ReaderNovelChapter> {

    /**
     * 兼容读取仍保存在旧表中的小说正文，保障分表迁移期间历史数据可读。
     *
     * @param chapterId 章节ID
     * @return 旧表中的正文内容
     */
    @Select("select content from reader_novel_chapter where id = #{chapterId} limit 1")
    String selectLegacyContentByChapterId(@Param("chapterId") Long chapterId);
}
