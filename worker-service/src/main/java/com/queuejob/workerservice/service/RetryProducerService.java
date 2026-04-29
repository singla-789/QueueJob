package com.queuejob.workerservice.service;

import com.queuejob.workerservice.config.KafkaTopicConfig;
import com.queuejob.workerservice.event.JobSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Handles retry publishing and dead-letter routing for failed jobs.
 *
 * <ul>
 *   <li><b>Retry:</b> increments retryCount, waits for exponential backoff
 *       (2^retryCount seconds, capped), then re-publishes to the same priority topic.</li>
 *   <li><b>Dead-letter:</b> publishes the event to {@code jobs.dead-letter} with
 *       error details in Kafka headers.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${worker.retry.max-backoff-seconds:60}")
    private int maxBackoffSeconds;

    /**
     * Applies exponential backoff and re-publishes the job to its original topic.
     * This method blocks the calling thread for the backoff duration, which is
     * acceptable because it runs inside a priority-specific @Async thread pool.
     */
    public void retryJob(JobSubmittedEvent event, String originalTopic) {
        int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
        int newRetryCount = currentRetry + 1;
        event.setRetryCount(newRetryCount);

        // Exponential backoff: 2^retryCount seconds, capped at maxBackoffSeconds
        long backoffSeconds = Math.min((long) Math.pow(2, newRetryCount), maxBackoffSeconds);

        log.info("Scheduling retry #{} for job {} — backoff {}s before re-publishing to {}",
                newRetryCount, event.getJobId(), backoffSeconds, originalTopic);

        try {
            Thread.sleep(backoffSeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff interrupted for job {} retry #{}", event.getJobId(), newRetryCount);
            return;
        }

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(originalTopic, event.getJobId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to re-publish job {} to {} for retry #{}: {}",
                        event.getJobId(), originalTopic, newRetryCount, ex.getMessage(), ex);
            } else {
                log.info("Re-published job {} to {} for retry #{} [partition={}, offset={}]",
                        event.getJobId(), originalTopic, newRetryCount,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publishes the failed job to the dead-letter topic with error details
     * stored in Kafka headers for downstream consumption.
     */
    public void sendToDeadLetter(JobSubmittedEvent event, String errorMessage, String stackTrace) {
        String topic = KafkaTopicConfig.TOPIC_DEAD_LETTER;

        log.warn("Sending job {} to dead-letter topic {} — max retries ({}) exceeded",
                event.getJobId(), topic, event.getMaxRetries());

        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, event.getJobId().toString(), event);

        // Attach error details as Kafka headers
        if (errorMessage != null) {
            record.headers().add(new RecordHeader("error-message",
                    errorMessage.getBytes(StandardCharsets.UTF_8)));
        }
        if (stackTrace != null) {
            // Truncate stack trace to avoid exceeding Kafka header size limits
            String truncated = stackTrace.length() > 10_000
                    ? stackTrace.substring(0, 10_000) + "\n... [truncated]"
                    : stackTrace;
            record.headers().add(new RecordHeader("error-stack-trace",
                    truncated.getBytes(StandardCharsets.UTF_8)));
        }

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send job {} to dead-letter topic: {}",
                        event.getJobId(), ex.getMessage(), ex);
            } else {
                log.info("Sent job {} to dead-letter topic [partition={}, offset={}]",
                        event.getJobId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
