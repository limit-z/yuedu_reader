package org.dromara.reader.enums;

/**
 * 阅读器模块代码，承载 ImportTaskStatus 相关业务能力。
 */
public enum ImportTaskStatus {
    CREATED,
    UPLOADED,
    PARSING,
    PARSE_FAILED,
    CLEANING,
    PENDING_REVIEW,
    CANCELED,
    COMPLETED
}
