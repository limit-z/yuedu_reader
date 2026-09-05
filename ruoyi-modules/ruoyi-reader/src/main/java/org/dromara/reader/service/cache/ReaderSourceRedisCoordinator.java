package org.dromara.reader.service.cache;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.config.ReaderSourceWorkerProperties;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/** 采集任务的 Redis 租约、站点限流、熔断和幂等协调器。 */
@RequiredArgsConstructor
@Service
public class ReaderSourceRedisCoordinator {

    private static final String KEY_PREFIX = "reader:source:";
    private final RedissonClient redissonClient;
    private final ReaderSourceWorkerProperties properties;

    /** 让同一运行记录在租约有效期内只被一个 Worker 领取。 */
    public boolean claimRun(Long runId, String workerId) {
        return claimBucket(runId).setIfAbsent(workerId, Duration.ofSeconds(properties.getClaimLeaseSeconds()));
    }

    public boolean ownsRun(Long runId, String workerId) {
        return workerId != null && workerId.equals(claimBucket(runId).get());
    }

    public boolean renewRun(Long runId, String workerId) {
        RBucket<String> bucket = claimBucket(runId);
        if (!workerId.equals(bucket.get())) {
            return false;
        }
        bucket.expire(Duration.ofSeconds(properties.getClaimLeaseSeconds()));
        return true;
    }

    public void releaseRun(Long runId, String workerId) {
        RBucket<String> bucket = claimBucket(runId);
        if (workerId != null && workerId.equals(bucket.get())) {
            bucket.delete();
        }
    }

    public RLock resultLock(Long runId) {
        return redissonClient.getLock(KEY_PREFIX + "result:lock:" + runId);
    }

    public boolean isBatchProcessed(Long runId, String batchId) {
        return Boolean.TRUE.equals(batchBucket(runId, batchId).get());
    }

    public void markBatchProcessed(Long runId, String batchId) {
        batchBucket(runId, batchId).set(Boolean.TRUE, Duration.ofDays(2));
    }

    /**
     * 按策略申请一次站点访问许可。限流发生在 Java 编排层，所有执行器共享同一组 Redis 状态。
     */
    public Permit acquireSitePermit(Long siteId, int requestsPerMinute, int dailyRequestLimit, int minDelayMs) {
        if (isCircuitOpen(siteId)) {
            return Permit.denied(60_000L, "站点熔断中，请等待人工复核或自动恢复");
        }
        RLock lock = redissonClient.getLock(KEY_PREFIX + "site:delay:lock:" + siteId);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (!locked) {
                return Permit.denied(200L, "站点访问许可正在由其他 Worker 申请");
            }
            long now = System.currentTimeMillis();
            RAtomicLong lastRequest = redissonClient.getAtomicLong(KEY_PREFIX + "site:last-request:" + siteId);
            long previous = lastRequest.get();
            long remainingDelay = minDelayMs - (now - previous);
            if (remainingDelay > 0) {
                return Permit.denied(remainingDelay, "未达到站点最小请求间隔");
            }

            RRateLimiter rateLimiter = redissonClient.getRateLimiter(
                KEY_PREFIX + "site:rate:" + siteId + ":" + requestsPerMinute);
            rateLimiter.trySetRate(RateType.OVERALL, requestsPerMinute, Duration.ofMinutes(1));
            if (!rateLimiter.tryAcquire()) {
                return Permit.denied(6_000L, "已达到站点每分钟请求上限");
            }

            RAtomicLong dailyCounter = redissonClient.getAtomicLong(
                KEY_PREFIX + "site:daily:" + siteId + ":" + LocalDate.now());
            long daily = dailyCounter.incrementAndGet();
            dailyCounter.expire(Duration.ofDays(2));
            if (daily > dailyRequestLimit) {
                dailyCounter.decrementAndGet();
                return Permit.denied(60_000L, "已达到站点每日请求上限");
            }
            lastRequest.set(now);
            return Permit.granted();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Permit.denied(1_000L, "访问许可申请被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public boolean recordFailure(Long siteId, int threshold) {
        RAtomicLong failures = redissonClient.getAtomicLong(KEY_PREFIX + "site:failures:" + siteId);
        long count = failures.incrementAndGet();
        failures.expire(Duration.ofHours(2));
        if (count < threshold) {
            return false;
        }
        redissonClient.getBucket(KEY_PREFIX + "site:circuit:" + siteId)
            .set(Boolean.TRUE, Duration.ofMinutes(10));
        return true;
    }

    public void recordSuccess(Long siteId) {
        redissonClient.getAtomicLong(KEY_PREFIX + "site:failures:" + siteId).delete();
        redissonClient.getBucket(KEY_PREFIX + "site:circuit:" + siteId).delete();
    }

    public boolean isCircuitOpen(Long siteId) {
        return Boolean.TRUE.equals(redissonClient.getBucket(KEY_PREFIX + "site:circuit:" + siteId).get());
    }

    /** 防止同一个发现源被手工运行和定时任务同时访问。 */
    public RLock discoveryProviderLock(Long providerId) {
        return redissonClient.getLock(KEY_PREFIX + "discovery:provider:lock:" + providerId);
    }

    private RBucket<String> claimBucket(Long runId) {
        return redissonClient.getBucket(KEY_PREFIX + "run:claim:" + runId);
    }

    private RBucket<Boolean> batchBucket(Long runId, String batchId) {
        return redissonClient.getBucket(KEY_PREFIX + "run:batch:" + runId + ":" + batchId);
    }

    public record Permit(boolean allowed, long retryAfterMs, String reason) {
        public static Permit granted() {
            return new Permit(true, 0L, null);
        }

        public static Permit denied(long retryAfterMs, String reason) {
            return new Permit(false, Math.max(100L, retryAfterMs), reason);
        }
    }
}
