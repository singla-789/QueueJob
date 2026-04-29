package com.queuejob.workerservice.service;

import com.queuejob.workerservice.client.JobServiceClient;
import com.queuejob.workerservice.config.KafkaTopicConfig;
import com.queuejob.workerservice.event.JobCompletedEvent;
import com.queuejob.workerservice.event.JobSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * Processes jobs received from Kafka.
 * Each priority level is dispatched to a separate thread pool.
 *
 * <p>On success, the service publishes a {@link JobCompletedEvent} to the
 * {@code jobs.completed} topic for notification-service consumption.</p>
 *
 * <p>On failure, the service applies retry logic:
 * <ul>
 *   <li>If retries remain → status is set to RETRYING, the job is re-published
 *       with exponential backoff via {@link RetryProducerService}.</li>
 *   <li>If max retries exceeded → status is set to FAILED, the job is routed
 *       to the dead-letter topic.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessorService {

    private final JobServiceClient jobServiceClient;
    private final RetryProducerService retryProducerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Async("highPriorityExecutor")
    public void processHighPriority(JobSubmittedEvent event, String topic) {
        processJob(event, "HIGH", topic);
    }

    @Async("mediumPriorityExecutor")
    public void processMediumPriority(JobSubmittedEvent event, String topic) {
        processJob(event, "MEDIUM", topic);
    }

    @Async("lowPriorityExecutor")
    public void processLowPriority(JobSubmittedEvent event, String topic) {
        processJob(event, "LOW", topic);
    }

    private void processJob(JobSubmittedEvent event, String priorityLabel, String topic) {
        int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;

        log.info("[{}] Processing job {} of type '{}' (retry {}/{}) on thread {}",
                priorityLabel, event.getJobId(), event.getType(),
                currentRetry, event.getMaxRetries(),
                Thread.currentThread().getName());

        // Notify job-service that processing has started
        jobServiceClient.updateJobStatus(event.getJobId(), "PROCESSING", null);

        try {
            // ── Simulate job processing ─────────────────────────────
            // Replace this block with actual job execution logic
            // based on event.getType() and event.getPayload()
            executeJob(event);

            // Notify job-service of successful completion
            jobServiceClient.updateJobStatus(event.getJobId(), "COMPLETED", null);

            // Publish completion event for notification-service
            publishJobCompleted(event);

            log.info("[{}] Job {} completed successfully", priorityLabel, event.getJobId());

        } catch (Exception ex) {
            log.error("[{}] Job {} failed (retry {}/{}): {}",
                    priorityLabel, event.getJobId(),
                    currentRetry, event.getMaxRetries(),
                    ex.getMessage(), ex);

            handleJobFailure(event, ex, topic);
        }
    }

    /**
     * Publishes a {@link JobCompletedEvent} to the {@code jobs.completed} Kafka topic.
     * This triggers email notifications via the notification-service.
     */
    private void publishJobCompleted(JobSubmittedEvent event) {
        JobCompletedEvent completedEvent = JobCompletedEvent.builder()
                .jobId(event.getJobId())
                .type(event.getType())
                .payload(event.getPayload())
                .priority(event.getPriority())
                .retryCount(event.getRetryCount())
                .createdAt(event.getCreatedAt())
                .completedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(
                KafkaTopicConfig.TOPIC_JOBS_COMPLETED,
                event.getJobId().toString(),
                completedEvent
        ).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish completion event for job {}: {}",
                        event.getJobId(), ex.getMessage(), ex);
            } else {
                log.info("Published completion event for job {} to {} [partition={}, offset={}]",
                        event.getJobId(), KafkaTopicConfig.TOPIC_JOBS_COMPLETED,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Decides whether to retry the job or send it to the dead-letter topic.
     */
    private void handleJobFailure(JobSubmittedEvent event, Exception ex, String topic) {
        int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
        int maxRetries = event.getMaxRetries() != null ? event.getMaxRetries() : 3;

        String stackTrace = getStackTrace(ex);

        if (currentRetry < maxRetries) {
            // Retries remaining — mark as RETRYING and re-publish with backoff
            log.info("Job {} has retries remaining ({}/{}). Scheduling retry...",
                    event.getJobId(), currentRetry, maxRetries);

            jobServiceClient.updateJobStatus(event.getJobId(), "RETRYING", ex.getMessage());
            retryProducerService.retryJob(event, topic);

        } else {
            // Max retries exceeded — mark as FAILED and route to dead-letter
            log.warn("Job {} has exhausted all retries ({}/{}). Sending to dead-letter.",
                    event.getJobId(), currentRetry, maxRetries);

            jobServiceClient.updateJobStatus(event.getJobId(), "FAILED", ex.getMessage());
            retryProducerService.sendToDeadLetter(event, ex.getMessage(), stackTrace);
        }
    }

    /**
     * Execute the actual job work. Override or extend this method
     * to add real processing logic based on job type.
     */
    private void executeJob(JobSubmittedEvent event) {
        // Placeholder — simulate work with a short delay
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job processing interrupted", e);
        }

        log.debug("Executed job {} with payload: {}", event.getJobId(), event.getPayload());
    }

    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
