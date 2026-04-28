package com.queuejob.jobservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_HIGH_PRIORITY   = "jobs.high-priority";
    public static final String TOPIC_MEDIUM_PRIORITY = "jobs.medium-priority";
    public static final String TOPIC_LOW_PRIORITY    = "jobs.low-priority";

    @Bean
    public NewTopic highPriorityTopic() {
        return TopicBuilder.name(TOPIC_HIGH_PRIORITY)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic mediumPriorityTopic() {
        return TopicBuilder.name(TOPIC_MEDIUM_PRIORITY)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic lowPriorityTopic() {
        return TopicBuilder.name(TOPIC_LOW_PRIORITY)
                .partitions(2)
                .replicas(1)
                .build();
    }
}
