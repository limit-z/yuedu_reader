package org.dromara.reader.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;

/**
 * 阅读器 Redis 客户端封装，提供模块内统一的缓存访问入口。
 */
@Slf4j
@Service
public class ReaderRedisCacheClient {

    /**
     * 写入缓存对象。
     */
    public <T> void setObject(String key, T value) {
        RedisUtils.setCacheObject(key, value);
    }

    /**
     * 按有效期写入缓存对象。
     */
    public <T> void setObject(String key, T value, Duration duration) {
        RedisUtils.setCacheObject(key, value, duration);
    }

    /**
     * 读取缓存对象。
     */
    public <T> T getObject(String key) {
        return RedisUtils.getCacheObject(key);
    }

    /**
     * 安全读取缓存对象。
     */
    public <T> T getObjectSafely(String key) {
        try {
            return RedisUtils.getCacheObject(key);
        } catch (Exception ex) {
            // 目录等列表结构升级后，旧缓存可能因为反序列化类型不兼容而报错，这里直接删掉坏缓存并回源数据库。
            log.warn("reader cache decode failed, fallback to db, key={}", key, ex);
            deleteObject(key);
            return null;
        }
    }

    /**
     * 删除单个缓存对象。
     */
    public boolean deleteObject(String key) {
        return RedisUtils.deleteObject(key);
    }

    /**
     * 批量删除缓存对象。
     */
    public void deleteObjects(Collection<?> keys) {
        RedisUtils.deleteObject(keys);
    }

    /**
     * 按前缀扫描删除缓存，供作品发布状态变化时清理分页和正文缓存。
     */
    public void deleteByPattern(String pattern) {
        RedisUtils.deleteKeys(pattern);
    }
}
