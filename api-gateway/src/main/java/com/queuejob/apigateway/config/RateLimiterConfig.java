package com.queuejob.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configures the key resolver used by the Redis-based rate limiter.
 *
 * <p>The rate limiter uses the API key from the {@code X-API-Key} header
 * as the bucket key. If no API key is present, falls back to the client
 * IP address to prevent unauthenticated abuse.</p>
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver apiKeyKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.isBlank()) {
                return Mono.just(apiKey);
            }
            // Fallback to client IP
            return Mono.just(
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "anonymous"
            );
        };
    }
}
