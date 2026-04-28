package com.queuejob.jobservice.service;

import com.queuejob.jobservice.dto.CreateJobRequest;
import com.queuejob.jobservice.dto.JobResponse;
import com.queuejob.jobservice.dto.UpdateJobStatusRequest;
import com.queuejob.jobservice.entity.Job;
import com.queuejob.jobservice.entity.JobAuditLog;
import com.queuejob.jobservice.entity.enums.JobPriority;
import com.queuejob.jobservice.entity.enums.JobStatus;
import com.queuejob.jobservice.exception.InvalidJobStateException;
import com.queuejob.jobservice.exception.JobNotFoundException;
import com.queuejob.jobservice.repository.JobAuditLogRepository;
import com.queuejob.jobservice.repository.JobRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobAuditLogRepository auditLogRepository;
    private final JobKafkaProducer kafkaProducer;
    private final JobCacheService cacheService;
    private final RateLimiterService rateLimiterService;

    // ── Create ───────────────────────────────────────────────────────

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        // Enforce rate limit: max 100 jobs/min per job type
        rateLimiterService.checkRateLimit(request.getType());

        Job job = Job.builder()
                .type(request.getType())
                .payload(request.getPayload())
                .priority(request.getPriority() != null ? request.getPriority() : JobPriority.MEDIUM)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .scheduledAt(request.getScheduledAt())
                .status(request.getScheduledAt() != null ? JobStatus.SCHEDULED : JobStatus.PENDING)
                .build();

        Job saved = jobRepository.save(job);

        auditLogRepository.save(JobAuditLog.builder()
                .jobId(saved.getId())
                .oldStatus(null)
                .newStatus(saved.getStatus())
                .message("Job created")
                .build());

        log.info("Created job {} of type '{}' with status {}", saved.getId(), saved.getType(), saved.getStatus());

        // Publish to Kafka for worker consumption
        if (saved.getStatus() == JobStatus.PENDING) {
            saved.setStatus(JobStatus.QUEUED);
            saved = jobRepository.save(saved);

            auditLogRepository.save(JobAuditLog.builder()
                    .jobId(saved.getId())
                    .oldStatus(JobStatus.PENDING)
                    .newStatus(JobStatus.QUEUED)
                    .message("Job queued to Kafka")
                    .build());

            kafkaProducer.publishJob(saved);
        }

        // Cache the newly created job
        JobResponse response = toResponse(saved);
        cacheService.put(saved.getId(), response);

        return response;
    }

    // ── Get by ID (Redis-first, PostgreSQL fallback) ─────────────────

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId) {
        // Try Redis cache first
        JobResponse cached = cacheService.get(jobId);
        if (cached != null) {
            return cached;
        }

        // Cache miss — read from PostgreSQL
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        JobResponse response = toResponse(job);

        // Populate cache for next read
        cacheService.put(jobId, response);

        return response;
    }

    // ── List with filters ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs(JobStatus status, String type) {
        Specification<Job> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return jobRepository.findAll(spec)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Cancel ───────────────────────────────────────────────────────

    @Transactional
    public JobResponse cancelJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.SCHEDULED) {
            throw new InvalidJobStateException(
                    String.format("Cannot cancel job %s — current status is %s. Only PENDING or SCHEDULED jobs can be cancelled.",
                            jobId, job.getStatus()));
        }

        JobStatus oldStatus = job.getStatus();
        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        Job saved = jobRepository.save(job);

        auditLogRepository.save(JobAuditLog.builder()
                .jobId(saved.getId())
                .oldStatus(oldStatus)
                .newStatus(JobStatus.CANCELLED)
                .message("Job cancelled by user")
                .build());

        log.info("Cancelled job {}", jobId);

        // Evict stale cache entry and re-cache with updated status
        JobResponse response = toResponse(saved);
        cacheService.put(jobId, response);

        return response;
    }

    // ── Update Status (called by worker-service) ─────────────────────

    @Transactional
    public JobResponse updateJobStatus(UUID jobId, UpdateJobStatusRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        JobStatus newStatus;
        try {
            newStatus = JobStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidJobStateException("Invalid status: " + request.getStatus());
        }

        JobStatus oldStatus = job.getStatus();
        job.setStatus(newStatus);

        if (request.getErrorMessage() != null) {
            job.setErrorMessage(request.getErrorMessage());
        }

        if (newStatus == JobStatus.COMPLETED || newStatus == JobStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }

        if (newStatus == JobStatus.RETRYING) {
            job.setRetryCount(job.getRetryCount() + 1);
        }

        Job saved = jobRepository.save(job);

        auditLogRepository.save(JobAuditLog.builder()
                .jobId(saved.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .message(request.getErrorMessage() != null
                        ? "Status updated by worker: " + request.getErrorMessage()
                        : "Status updated by worker")
                .build());

        log.info("Job {} status updated: {} → {}", jobId, oldStatus, newStatus);

        // Update the cache with latest state
        JobResponse response = toResponse(saved);
        cacheService.put(jobId, response);

        return response;
    }

    // ── Mapper ───────────────────────────────────────────────────────

    private JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .type(job.getType())
                .payload(job.getPayload())
                .status(job.getStatus())
                .priority(job.getPriority())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .createdAt(job.getCreatedAt())
                .scheduledAt(job.getScheduledAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
