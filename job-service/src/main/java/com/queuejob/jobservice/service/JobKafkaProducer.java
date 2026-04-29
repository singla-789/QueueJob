package com.queuejob.jobservice.service;

import com.queuejob.jobservice.config.KafkaTopicConfig;
import com.queuejob.jobservice.entity.Job;
import com.queuejob.jobservice.entity.enums.JobPriority;
import com.queuejob.jobservice.event.JobSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobKafkaProducer {

    private final KafkaTemplate<String, JobSubmittedEvent> kafkaTemplate;

    /**
     * Publishes a job event to the Kafka topic matching its priority level.
     */
    public void publishJob(Job job) {
        String topic = resolveTopic(job.getPriority());

        JobSubmittedEvent event = JobSubmittedEvent.builder()
                .jobId(job.getId())
                .type(job.getType())
                .payload(job.getPayload())
                .priority(job.getPriority().name())
                .maxRetries(job.getMaxRetries())
                .retryCount(0)
                .createdAt(job.getCreatedAt())
                .scheduledAt(job.getScheduledAt())
                .build();

        CompletableFuture<SendResult<String, JobSubmittedEvent>> future =
                kafkaTemplate.send(topic, job.getId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish job {} to topic {}: {}",
                        job.getId(), topic, ex.getMessage(), ex);
            } else {
                log.info("Published job {} to topic {} [partition={}, offset={}]",
                        job.getId(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private String resolveTopic(JobPriority priority) {
        return switch (priority) {
            case CRITICAL, HIGH -> KafkaTopicConfig.TOPIC_HIGH_PRIORITY;
            case MEDIUM         -> KafkaTopicConfig.TOPIC_MEDIUM_PRIORITY;
            case LOW            -> KafkaTopicConfig.TOPIC_LOW_PRIORITY;
        };
    }
}
