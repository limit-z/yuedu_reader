package org.dromara.reader.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;

/** 来源章节快照数据访问层。 */
public interface ReaderSourceChapterSnapshotMapper extends BaseMapperPlus<ReaderSourceChapterSnapshot, ReaderSourceChapterSnapshot> {

    @Select("select * from reader_source_chapter_snapshot where task_id = #{taskId} "
        + "and source_chapter_id = #{sourceChapterId} and content_hash = #{contentHash} "
        + "order by id desc limit 1")
    ReaderSourceChapterSnapshot selectByContentHash(@Param("taskId") Long taskId,
                                                     @Param("sourceChapterId") String sourceChapterId,
                                                     @Param("contentHash") String contentHash);

    @Select("select * from reader_source_chapter_snapshot where task_id = #{taskId} "
        + "and source_chapter_id = #{sourceChapterId} order by id desc limit 1")
    ReaderSourceChapterSnapshot selectLatestByChapter(@Param("taskId") Long taskId,
                                                      @Param("sourceChapterId") String sourceChapterId);
}
