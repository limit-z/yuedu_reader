package org.dromara.reader.service.parser;

import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ReaderAllEnvTest
public class ReaderFileParserTest {

    @Test
    public void txtParserShouldSupportTxtSuffix() {
        ReaderFileParser parser = new NovelTxtParser();
        assertTrue(parser.supports("txt"));
    }
}
