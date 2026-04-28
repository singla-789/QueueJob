package com.queuejob.jobservice.dto;

import com.queuejob.jobservice.entity.enums.JobPriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobRequest {

    @NotBlank(message = "Job type is required")
    @Size(max = 100, message = "Job type must not exceed 100 characters")
    private String type;

    @NotBlank(message = "Payload is required")
    private String payload;

    private JobPriority priority;

    @Min(value = 0, message = "Max retries must be >= 0")
    private Integer maxRetries;

    private LocalDateTime scheduledAt;
}
