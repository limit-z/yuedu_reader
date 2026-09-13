package org.dromara.reader.service;

import org.dromara.reader.service.impl.ReaderNovelTextFormatter;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ReaderAllEnvTest
class ReaderNovelTextFormatterTest {

    @Test
    void shouldConvertSourceSpacingIntoReadableParagraphs() {
        String source = "第一段。    第二段。\n\n  第三段。  ";

        assertEquals("第一段。\n\n第二段。\n\n第三段。", ReaderNovelTextFormatter.format(source));
    }

    @Test
    void shouldKeepSingleSpacesInsideAParagraph() {
        assertEquals("A B 测试。", ReaderNovelTextFormatter.format("  A B 测试。  "));
    }

    @Test
    void shouldConvertFullWidthIndentSpacingIntoParagraphs() {
        assertEquals("第一段。\n\n第二段。", ReaderNovelTextFormatter.format("第一段。　　第二段。"));
    }
}
