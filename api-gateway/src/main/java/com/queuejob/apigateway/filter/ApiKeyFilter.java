package com.queuejob.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global API key authentication filter.
 *
 * <p>Reads the {@code X-API-Key} header from every incoming request and
 * validates it against a set of valid keys stored in a Redis SET named
 * {@code gateway:api-keys}.</p>
 *
 * <p>Returns {@code 401 UNAUTHORIZED} if the key is missing or invalid.</p>
 *
 * <p>Runs before all other filters (order = -1).</p>
 */
@Slf4j
@Component
public class ApiKeyFilter implements GlobalFilter, Ordered {

    private static final String REDIS_API_KEYS_SET = "gateway:api-keys";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${gateway.api-key.enabled:true}")
    private boolean enabled;

    @Value("${gateway.api-key.header-name:X-API-Key}")
    private String headerName;

    @Value("${gateway.api-key.excluded-paths:/actuator/**}")
    private List<String> excludedPaths;

    public ApiKeyFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if API key validation is disabled
        if (!enabled) {
            return chain.filter(exchange);
        }

        // Skip excluded paths (e.g. actuator)
        String requestPath = exchange.getRequest().getPath().value();
        for (String pattern : excludedPaths) {
            if (PATH_MATCHER.match(pattern, requestPath)) {
                return chain.filter(exchange);
            }
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(headerName);

        // Missing API key
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Request to {} rejected — missing {} header", requestPath, headerName);
            return unauthorizedResponse(exchange, "Missing API key. Provide a valid key in the " + headerName + " header.");
        }

        // Validate against Redis
        return redisTemplate.opsForSet().isMember(REDIS_API_KEYS_SET, apiKey)
                .flatMap(isMember -> {
                    if (Boolean.TRUE.equals(isMember)) {
                        log.debug("API key validated for request to {}", requestPath);
                        return chain.filter(exchange);
                    } else {
                        log.warn("Request to {} rejected — invalid API key", requestPath);
                        return unauthorizedResponse(exchange, "Invalid API key.");
                    }
                });
    }

    /**
     * Runs before all other global filters.
     */
    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"error": "Unauthorized", "message": "%s"}
                """.formatted(message).trim();

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }
}
