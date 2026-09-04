package org.dromara.reader.service.parser;

/**
 * 阅读器文件解析器接口，定义不同导入格式的统一解析能力。
 */
public interface ReaderFileParser {

    /**
     * 判断当前解析器是否支持指定文件后缀。
     */
    boolean supports(String suffix);

    /**
     * 解析导入任务对应的源文件。
     */
    void parse(Long importTaskId);
}
