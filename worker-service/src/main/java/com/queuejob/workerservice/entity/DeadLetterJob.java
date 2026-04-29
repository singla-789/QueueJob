package com.queuejob.workerservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists jobs that have exhausted all retry attempts.
 * Contains the full error message and stack trace for debugging.
 */
@Entity
@Table(name = "dead_letter_jobs", indexes = {
        @Index(name = "idx_dead_letter_job_id", columnList = "job_id"),
        @Index(name = "idx_dead_letter_failed_at", columnList = "failed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "original_topic", length = 100)
    private String originalTopic;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private LocalDateTime failedAt;

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) {
            failedAt = LocalDateTime.now();
        }
    }
}
