package com.queuejob.workerservice.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published to {@code jobs.completed} Kafka topic
 * when a job finishes processing successfully.
 * Consumed by the notification-service to send email alerts.
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
