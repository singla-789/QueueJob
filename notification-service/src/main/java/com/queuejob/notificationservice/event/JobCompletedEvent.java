package com.queuejob.notificationservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event consumed from the {@code jobs.completed} Kafka topic.
 * Published by the worker-service when a job finishes successfully.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCompletedEvent {

    private UUID jobId;
    private String type;
    private String payload;
    private String priority;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
