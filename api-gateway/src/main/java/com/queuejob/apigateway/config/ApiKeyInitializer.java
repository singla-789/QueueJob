package com.queuejob.apigateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds initial API keys into Redis on startup.
 *
 * <p>In production, API keys should be managed via an admin interface
 * or a secrets manager. This initializer provides default keys for
 * development and testing.</p>
 *
 * <p>Keys are stored in a Redis SET named {@code gateway:api-keys}.
 * Use {@code SADD gateway:api-keys <key>} to add keys manually.</p>
 */
@Slf4j
@Component
public class ApiKeyInitializer {

    private static final String REDIS_API_KEYS_SET = "gateway:api-keys";

    private final ReactiveStringRedisTemplate redisTemplate;

    public ApiKeyInitializer(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void seedDefaultApiKeys() {
        String[] defaultKeys = {
                "queuejob-dev-key-001",
                "queuejob-dev-key-002",
                "queuejob-test-key-001"
        };

        for (String key : defaultKeys) {
            redisTemplate.opsForSet().add(REDIS_API_KEYS_SET, key)
                    .subscribe(added -> {
                        if (added != null && added > 0) {
                            log.info("Seeded API key: {}...{}", key.substring(0, 10), key.substring(key.length() - 3));
                        }
                    });
        }

        log.info("API key initialization complete. {} default keys configured.", defaultKeys.length);
    }
}
