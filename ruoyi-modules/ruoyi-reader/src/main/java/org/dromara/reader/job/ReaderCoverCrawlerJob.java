package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.reader.service.impl.ReaderCoverCrawlerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 扫描并异步执行章节采集完成后产生的封面任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReaderCoverCrawlerJob {
    private final ReaderCoverCrawlerService crawlerService;

    @Scheduled(fixedDelayString = "${reader.cover.crawler.scheduler-interval-ms:10000}")
    public void execute() {
        try {
            crawlerService.dispatchPendingTasks();
        } catch (Exception ex) {
            log.warn("封面采集任务调度失败：{}", ex.getMessage());
        }
    }
}
