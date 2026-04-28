package com.queuejob.jobservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateJobStatusRequest {

    @NotNull(message = "Status is required")
    @NotBlank(message = "Status must not be blank")
    private String status;

    private String errorMessage;

    private UUID workerId;
}
