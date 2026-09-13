package org.dromara.reader.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 阅读器自动封面的存储和公开地址配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "reader.cover")
public class ReaderCoverProperties {
    private String storagePath = "./data/reader-covers";
    private String publicPath = "/reader/app/covers";
}
