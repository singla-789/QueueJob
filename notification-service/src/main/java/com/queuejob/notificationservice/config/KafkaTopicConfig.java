package com.queuejob.notificationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Ensures the jobs.completed topic exists when the notification-service starts.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_JOBS_COMPLETED = "jobs.completed";

    @Bean
    public NewTopic jobsCompletedTopic() {
        return TopicBuilder.name(TOPIC_JOBS_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
