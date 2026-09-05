package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.reader.service.IReaderSourceDiscoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 按发现源自己的轮询时间执行合规的候选地址发现。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReaderSourceDiscoveryJob {

    private final IReaderSourceDiscoveryService discoveryService;

    @Scheduled(fixedDelayString = "${reader.source.discovery.scheduler-interval-ms:60000}")
    public void execute() {
        try {
            discoveryService.runDueProviders();
        } catch (Exception ex) {
            log.warn("书源发现调度执行失败：{}", ex.getMessage());
        }
    }
}
