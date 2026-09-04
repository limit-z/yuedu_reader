package org.dromara.reader.service.parser;

import org.springframework.stereotype.Component;

/**
 * 小说 TXT 解析器，负责把纯文本文件拆解为阅读器可消费的结构。
 */
@Component
public class NovelTxtParser implements ReaderFileParser {

    /**
     * 判断是否支持 TXT 文本解析。
     */
    @Override
    public boolean supports(String suffix) {
        return "txt".equalsIgnoreCase(suffix);
    }

    /**
     * 解析 TXT 导入任务。
     */
    @Override
    public void parse(Long importTaskId) {
        // 当前 P0 阶段的 TXT 解析逻辑已收口到 ReaderImportTaskServiceImpl，这里先保留扩展点。
    }
}
