package com.queuejob.jobservice.service;

import com.queuejob.jobservice.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed sliding-window rate limiter.
 * Enforces a maximum of 100 job submissions per minute per job type.
 *
 * Key format: rate_limit:job_type:{type}
 * Strategy:  INCR + EXPIRE (fixed window for simplicity and atomicity)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "rate_limit:job_type:";
    private static final long MAX_REQUESTS_PER_WINDOW = 100;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Check and increment the rate counter for a given job type.
     *
     * @param jobType the job type to rate-limit
     * @throws RateLimitExceededException if the limit is exceeded
     */
    public void checkRateLimit(String jobType) {
        String key = KEY_PREFIX + jobType;

        Long currentCount = stringRedisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            log.warn("Redis INCR returned null for key {}, skipping rate limit", key);
            return;
        }

        // Set expiry only on first request in the window
        if (currentCount == 1) {
            stringRedisTemplate.expire(key, WINDOW_DURATION);
        }

        if (currentCount > MAX_REQUESTS_PER_WINDOW) {
            Long ttl = stringRedisTemplate.getExpire(key);
            log.warn("Rate limit exceeded for job type '{}': {}/{} (resets in {}s)",
                    jobType, currentCount, MAX_REQUESTS_PER_WINDOW, ttl);

            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded for job type '%s'. Max %d jobs per minute. Try again in %d seconds.",
                            jobType, MAX_REQUESTS_PER_WINDOW, ttl != null ? ttl : 60));
        }

        log.debug("Rate limit for '{}': {}/{}", jobType, currentCount, MAX_REQUESTS_PER_WINDOW);
    }
}
