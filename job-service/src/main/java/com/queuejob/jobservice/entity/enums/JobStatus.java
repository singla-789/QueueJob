package com.queuejob.jobservice.entity.enums;

public enum JobStatus {
    PENDING,
    SCHEDULED,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETRYING
}
