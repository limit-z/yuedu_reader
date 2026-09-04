package org.dromara.reader.domain;

import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ReaderAllEnvTest
public class ReaderWorkTest {

    @Test
    public void shouldAllowSettingBasicWorkFields() {
        ReaderWork work = new ReaderWork();
        work.setTitle("凡人修仙传");
        work.setWorkType(WorkType.NOVEL.name());
        work.setPublishStatus(PublishStatus.DRAFT.name());

        assertEquals("凡人修仙传", work.getTitle());
        assertEquals("NOVEL", work.getWorkType());
        assertEquals("DRAFT", work.getPublishStatus());
    }
}
