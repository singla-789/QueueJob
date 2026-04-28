package com.queuejob.workerservice.service;

import com.queuejob.workerservice.client.JobServiceClient;
import com.queuejob.workerservice.event.JobSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processes jobs received from Kafka.
 * Each priority level is dispatched to a separate thread pool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessorService {

    private final JobServiceClient jobServiceClient;

    @Async("highPriorityExecutor")
    public void processHighPriority(JobSubmittedEvent event) {
        processJob(event, "HIGH");
    }

    @Async("mediumPriorityExecutor")
    public void processMediumPriority(JobSubmittedEvent event) {
        processJob(event, "MEDIUM");
    }

    @Async("lowPriorityExecutor")
    public void processLowPriority(JobSubmittedEvent event) {
        processJob(event, "LOW");
    }

    private void processJob(JobSubmittedEvent event, String priorityLabel) {
        log.info("[{}] Processing job {} of type '{}' on thread {}",
                priorityLabel, event.getJobId(), event.getType(),
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

            log.info("[{}] Job {} completed successfully", priorityLabel, event.getJobId());

        } catch (Exception ex) {
            log.error("[{}] Job {} failed: {}", priorityLabel, event.getJobId(), ex.getMessage(), ex);

            // Notify job-service of failure
            jobServiceClient.updateJobStatus(event.getJobId(), "FAILED", ex.getMessage());
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
}
