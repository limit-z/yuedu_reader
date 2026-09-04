package org.dromara.reader.enums;

import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ReaderAllEnvTest
public class ReaderEnumTest {

    @Test
    public void shouldExposeExpectedPublishStatusCodes() {
        assertEquals("DRAFT", PublishStatus.DRAFT.name());
        assertEquals("PUBLISHED", PublishStatus.PUBLISHED.name());
    }
}
