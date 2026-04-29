package com.queuejob.workerservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Ensures the dead-letter and completed topics exist when the worker-service starts.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_DEAD_LETTER = "jobs.dead-letter";
    public static final String TOPIC_JOBS_COMPLETED = "jobs.completed";

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(TOPIC_DEAD_LETTER)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobsCompletedTopic() {
        return TopicBuilder.name(TOPIC_JOBS_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
