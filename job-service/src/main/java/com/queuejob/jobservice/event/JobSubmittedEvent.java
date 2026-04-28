package com.queuejob.jobservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published to Kafka when a job is submitted.
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
