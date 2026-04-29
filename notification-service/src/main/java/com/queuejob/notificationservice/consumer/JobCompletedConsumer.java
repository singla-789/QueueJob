package com.queuejob.notificationservice.consumer;

import com.queuejob.notificationservice.event.JobCompletedEvent;
import com.queuejob.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens to the {@code jobs.completed} topic.
 * When a job completes successfully, it triggers an email notification
 * with the job details via the {@link EmailService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCompletedConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = "jobs.completed",
            groupId = "notification-service-group"
    )
    public void onJobCompleted(
            @Payload JobCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received job completion event for job {} from {}[partition={}, offset={}]",
                event.getJobId(), topic, partition, offset);

        emailService.sendJobCompletedEmail(event);
    }
}
