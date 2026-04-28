package com.queuejob.jobservice.repository;

import com.queuejob.jobservice.entity.Job;
import com.queuejob.jobservice.entity.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByType(String type);

    List<Job> findByStatusAndType(JobStatus status, String type);
}
