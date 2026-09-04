package org.dromara.reader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Worker 接入安全和协议限制。共享密钥必须通过环境变量注入。 */
@Data
@Component
@ConfigurationProperties(prefix = "reader.source.worker")
public class ReaderSourceWorkerProperties {
    private String sharedSecret;
    private int claimLeaseSeconds = 90;
    private int maxItemsPerResult = 100;
    private int maxContentBytes = 2 * 1024 * 1024;
}
