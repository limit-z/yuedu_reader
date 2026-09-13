package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 自动封面背景配置。 */
@Data
public class ReaderCoverStyleBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** GLOBAL、COLOR 或 IMAGE；全局配置只接受 COLOR、IMAGE。 */
    private String mode;
    private String color;
    private Long backgroundOssId;
}
