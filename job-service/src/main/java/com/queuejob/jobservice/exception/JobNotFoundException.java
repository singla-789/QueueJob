package com.queuejob.jobservice.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID jobId) {
        super("Job not found with id: " + jobId);
    }

    public JobNotFoundException(String message) {
        super(message);
    }
}
