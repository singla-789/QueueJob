package com.queuejob.jobservice.dto;

import com.queuejob.jobservice.entity.enums.JobPriority;
import com.queuejob.jobservice.entity.enums.JobStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {

    private UUID id;
    private String type;
    private String payload;
    private JobStatus status;
    private JobPriority priority;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
