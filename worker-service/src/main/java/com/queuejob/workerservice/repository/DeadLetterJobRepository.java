package com.queuejob.workerservice.repository;

import com.queuejob.workerservice.entity.DeadLetterJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for dead-lettered jobs that have exhausted all retries.
 */
@Repository
public interface DeadLetterJobRepository extends JpaRepository<DeadLetterJob, UUID> {

    /**
     * Find all dead-letter entries for a specific job.
     */
    List<DeadLetterJob> findByJobId(UUID jobId);
}
