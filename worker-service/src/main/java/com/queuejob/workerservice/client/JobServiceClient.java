package com.queuejob.workerservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * REST client that calls job-service to update job status after processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobServiceClient {

    private final RestTemplate restTemplate;

    @Value("${worker.job-service.base-url}")
    private String jobServiceBaseUrl;

    /**
     * Updates the job status via PATCH /api/jobs/{id}/status.
     */
    public void updateJobStatus(UUID jobId, String status, String errorMessage) {
        String url = jobServiceBaseUrl + "/api/jobs/" + jobId + "/status";

        Map<String, String> body = new java.util.HashMap<>();
        body.put("status", status);
        if (errorMessage != null) {
            body.put("errorMessage", errorMessage);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.PATCH, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated job {} status to {}", jobId, status);
            } else {
                log.warn("Unexpected response updating job {} status: {}", jobId, response.getStatusCode());
            }
        } catch (RestClientException ex) {
            log.error("Failed to update job {} status to {}: {}", jobId, status, ex.getMessage(), ex);
        }
    }
}
