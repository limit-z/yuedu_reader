package org.dromara.reader.service.parser;

import org.springframework.stereotype.Component;

/**
 * 小说 EPUB 解析器，负责提取电子书正文并转换为章节内容。
 */
@Component
public class NovelEpubParser implements ReaderFileParser {

    /**
     * 判断是否支持 EPUB 解析。
     */
    @Override
    public boolean supports(String suffix) {
        return "epub".equalsIgnoreCase(suffix);
    }

    /**
     * 解析 EPUB 导入任务。
     */
    @Override
    public void parse(Long importTaskId) {
        // 当前 P0 阶段的 EPUB 解析逻辑已收口到 ReaderImportTaskServiceImpl，这里先保留扩展点。
    }
}
