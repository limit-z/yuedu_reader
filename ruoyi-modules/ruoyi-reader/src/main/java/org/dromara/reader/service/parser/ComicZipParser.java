package org.dromara.reader.service.parser;

import org.springframework.stereotype.Component;

/**
 * 漫画压缩包解析器，负责提取图片资源并组织为章节与页序。
 */
@Component
public class ComicZipParser implements ReaderFileParser {

    /**
     * 判断是否支持漫画压缩包解析。
     */
    @Override
    public boolean supports(String suffix) {
        return "zip".equalsIgnoreCase(suffix) || "cbz".equalsIgnoreCase(suffix);
    }

    /**
     * 解析漫画压缩包导入任务。
     */
    @Override
    public void parse(Long importTaskId) {
        // 当前 P0 阶段的压缩包解析逻辑已收口到 ReaderImportTaskServiceImpl，这里先保留扩展点。
    }
}
