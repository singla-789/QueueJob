package com.queuejob.jobservice.service;

import com.queuejob.jobservice.dto.JobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed cache for job responses.
 * - Key format: job:{uuid}
 * - TTL: 1 hour
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCacheService {

    private static final String KEY_PREFIX = "job:";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get a cached job by ID. Returns null on cache miss or error.
     */
    public JobResponse get(UUID jobId) {
        try {
            Object cached = redisTemplate.opsForValue().get(key(jobId));
            if (cached instanceof JobResponse response) {
                log.debug("Cache HIT for job {}", jobId);
                return response;
            }
        } catch (Exception ex) {
            log.warn("Redis read failed for job {}: {}", jobId, ex.getMessage());
        }
        log.debug("Cache MISS for job {}", jobId);
        return null;
    }

    /**
     * Cache a job response with 1-hour TTL.
     */
    public void put(UUID jobId, JobResponse response) {
        try {
            redisTemplate.opsForValue().set(key(jobId), response, TTL);
            log.debug("Cached job {} (TTL={})", jobId, TTL);
        } catch (Exception ex) {
            log.warn("Redis write failed for job {}: {}", jobId, ex.getMessage());
        }
    }

    /**
     * Evict a cached job entry (used after status updates).
     */
    public void evict(UUID jobId) {
        try {
            redisTemplate.delete(key(jobId));
            log.debug("Evicted cache for job {}", jobId);
        } catch (Exception ex) {
            log.warn("Redis delete failed for job {}: {}", jobId, ex.getMessage());
        }
    }

    private String key(UUID jobId) {
        return KEY_PREFIX + jobId.toString();
    }
}
