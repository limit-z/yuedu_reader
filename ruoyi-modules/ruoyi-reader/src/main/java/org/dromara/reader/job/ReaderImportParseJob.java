package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.service.parser.ReaderFileParser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阅读器导入解析任务，负责触发文件导入任务的异步解析流程。
 */
@Component
@RequiredArgsConstructor
public class ReaderImportParseJob {

    /**
     * 文件解析器列表，运行时按文件类型挑选对应的导入实现。
     */
    private final List<ReaderFileParser> parsers;

    /**
     * 执行单个导入任务的异步解析流程。
     */
    public void execute(Long importTaskId) {
        // load task -> choose parser -> parse -> update status
    }
}
