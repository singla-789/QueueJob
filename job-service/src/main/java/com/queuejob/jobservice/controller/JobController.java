package com.queuejob.jobservice.controller;

import com.queuejob.jobservice.dto.CreateJobRequest;
import com.queuejob.jobservice.dto.JobResponse;
import com.queuejob.jobservice.dto.UpdateJobStatusRequest;
import com.queuejob.jobservice.entity.enums.JobStatus;
import com.queuejob.jobservice.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * POST /api/jobs — Submit a new job.
     */
    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/jobs/{id} — Get job by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    /**
     * GET /api/jobs?status=PENDING&type=EMAIL — List jobs with optional filters.
     */
    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(jobService.listJobs(status, type));
    }

    /**
     * DELETE /api/jobs/{id} — Cancel a pending/scheduled job.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.cancelJob(id));
    }

    /**
     * PATCH /api/jobs/{id}/status — Update job status (used by worker-service).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<JobResponse> updateJobStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobStatusRequest request) {
        return ResponseEntity.ok(jobService.updateJobStatus(id, request));
    }
}
