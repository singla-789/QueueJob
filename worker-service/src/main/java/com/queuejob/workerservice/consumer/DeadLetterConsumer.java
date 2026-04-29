package com.queuejob.workerservice.consumer;

import com.queuejob.workerservice.entity.DeadLetterJob;
import com.queuejob.workerservice.event.JobSubmittedEvent;
import com.queuejob.workerservice.repository.DeadLetterJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes jobs from the {@code jobs.dead-letter} Kafka topic.
 * These are jobs that have exceeded their maximum retry count.
 *
 * <p>Each dead-lettered job is persisted to the {@code dead_letter_jobs}
 * table with the full error message and stack trace extracted from
 * Kafka headers.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final DeadLetterJobRepository deadLetterJobRepository;

    @KafkaListener(
            topics = "jobs.dead-letter",
            groupId = "worker-service-dlq-group",
            containerFactory = "lowPriorityKafkaListenerFactory"
    )
    public void consumeDeadLetter(
            @Payload JobSubmittedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            org.springframework.messaging.MessageHeaders messageHeaders) {

        log.warn("Received dead-letter job {} from {}[partition={}, offset={}]",
                event.getJobId(), topic, partition, offset);

        // Extract error details from Kafka headers
        String errorMessage = extractHeader(messageHeaders, "error-message");
        String stackTrace = extractHeader(messageHeaders, "error-stack-trace");

        // Determine original topic from the event's priority
        String originalTopic = resolveOriginalTopic(event.getPriority());

        // Persist to dead_letter_jobs table
        DeadLetterJob deadLetterJob = DeadLetterJob.builder()
                .jobId(event.getJobId())
                .type(event.getType())
                .payload(event.getPayload())
                .priority(event.getPriority())
                .retryCount(event.getRetryCount() != null ? event.getRetryCount() : 0)
                .maxRetries(event.getMaxRetries() != null ? event.getMaxRetries() : 3)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .originalTopic(originalTopic)
                .build();

        DeadLetterJob saved = deadLetterJobRepository.save(deadLetterJob);

        log.info("Persisted dead-letter job {} → dead_letter_jobs.id={}",
                event.getJobId(), saved.getId());
    }

    /**
     * Extracts a String value from a Kafka header by name.
     */
    private String extractHeader(org.springframework.messaging.MessageHeaders headers, String headerName) {
        Object value = headers.get(headerName);
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Maps the priority string back to the original Kafka topic name.
     */
    private String resolveOriginalTopic(String priority) {
        if (priority == null) return "unknown";
        return switch (priority.toUpperCase()) {
            case "CRITICAL", "HIGH" -> "jobs.high-priority";
            case "MEDIUM" -> "jobs.medium-priority";
            case "LOW" -> "jobs.low-priority";
            default -> "unknown";
        };
    }
}
