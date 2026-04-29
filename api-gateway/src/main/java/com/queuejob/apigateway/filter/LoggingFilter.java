package com.queuejob.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Global logging filter that captures request and response details.
 *
 * <p>On every incoming request, logs:
 * <ul>
 *   <li>HTTP method</li>
 *   <li>Request URI</li>
 *   <li>Client IP address</li>
 * </ul>
 *
 * <p>After the response, logs:
 * <ul>
 *   <li>Response status code</li>
 *   <li>Request duration in milliseconds</li>
 * </ul>
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().name();
        String uri = exchange.getRequest().getURI().toString();
        String clientIp = resolveClientIp(exchange);

        log.info("→ {} {} from {}", method, uri, clientIp);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

            log.info("← {} {} — {} in {}ms", method, uri, statusCode, duration);
        }));
    }

    /**
     * Returns the order of this filter. 0 means it runs after
     * security filters but before route-specific filters.
     */
    @Override
    public int getOrder() {
        return 0;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        // Prefer X-Forwarded-For header (behind reverse proxies / load balancers)
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
