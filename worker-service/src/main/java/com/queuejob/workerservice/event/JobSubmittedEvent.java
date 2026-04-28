package com.queuejob.workerservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirrors the JobSubmittedEvent from job-service for deserialization.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSubmittedEvent {

    private UUID jobId;
    private String type;
    private String payload;
    private String priority;
    private Integer maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
}
