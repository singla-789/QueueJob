package com.queuejob.workerservice.consumer;

import com.queuejob.workerservice.event.JobSubmittedEvent;
import com.queuejob.workerservice.service.JobProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumers for all three priority topics.
 * Each listener uses a different container factory with appropriate concurrency.
 * Processing is delegated to the JobProcessorService which uses priority-specific thread pools.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobKafkaConsumer {

    private final JobProcessorService processorService;

    @KafkaListener(
            topics = "jobs.high-priority",
            groupId = "worker-service-group",
            containerFactory = "highPriorityKafkaListenerFactory"
    )
    public void consumeHighPriority(
            @Payload JobSubmittedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received HIGH priority job {} from {}[partition={}, offset={}] (retry={})",
                event.getJobId(), topic, partition, offset, event.getRetryCount());

        processorService.processHighPriority(event, topic);
    }

    @KafkaListener(
            topics = "jobs.medium-priority",
            groupId = "worker-service-group",
            containerFactory = "mediumPriorityKafkaListenerFactory"
    )
    public void consumeMediumPriority(
            @Payload JobSubmittedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received MEDIUM priority job {} from {}[partition={}, offset={}] (retry={})",
                event.getJobId(), topic, partition, offset, event.getRetryCount());

        processorService.processMediumPriority(event, topic);
    }

    @KafkaListener(
            topics = "jobs.low-priority",
            groupId = "worker-service-group",
            containerFactory = "lowPriorityKafkaListenerFactory"
    )
    public void consumeLowPriority(
            @Payload JobSubmittedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received LOW priority job {} from {}[partition={}, offset={}] (retry={})",
                event.getJobId(), topic, partition, offset, event.getRetryCount());

        processorService.processLowPriority(event, topic);
    }
}
