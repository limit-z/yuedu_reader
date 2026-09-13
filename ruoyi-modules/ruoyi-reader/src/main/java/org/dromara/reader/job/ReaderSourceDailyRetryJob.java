package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.reader.service.IReaderSourceService;
import org.dromara.reader.service.IReaderSourceWorkerService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 跨过每日额度重置时间后，自动恢复因每日请求上限暂停的任务。 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class ReaderSourceDailyRetryJob {

    private final IReaderSourceService sourceService;
    private final IReaderSourceWorkerService workerService;

    /** 每分钟检查一次，服务在额度重置时短暂不可用也能在恢复后补偿。 */
    @Scheduled(fixedDelayString = "${reader.source.daily-retry.scheduler-interval-ms:60000}")
    public void execute() {
        try {
            int recovered = workerService.recoverStaleRuns();
            int synchronizedTasks = workerService.synchronizeTaskProgress();
            int count = sourceService.retryDailyLimitTasks();
            int recoverableRetries = sourceService.retryRecoverableTasks();
            if (count > 0 || recoverableRetries > 0 || recovered > 0 || synchronizedTasks > 0) {
                log.info("采集调度恢复：失联运行 {} 个，每日额度重试 {} 个，普通异常重试 {} 个，进度同步 {} 个任务",
                    recovered, count, recoverableRetries, synchronizedTasks);
            }
        } catch (Exception ex) {
            log.warn("每日额度自动重试调度失败：{}", ex.getMessage());
        }
    }
}
