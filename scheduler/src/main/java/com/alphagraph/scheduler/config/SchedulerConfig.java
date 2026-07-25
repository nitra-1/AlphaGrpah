package com.alphagraph.scheduler.config;

import com.alphagraph.common.quality.DataQualityEngine;
import com.alphagraph.scheduler.notification.LoggingNotificationPort;
import com.alphagraph.scheduler.notification.NotificationPort;
import com.alphagraph.scheduler.orchestration.PipelineOrchestrator;
import com.alphagraph.scheduler.persistence.PipelineExecutionRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerConfig {

    @Bean
    public NotificationPort notificationPort() {
        return new LoggingNotificationPort();
    }

    @Bean
    public DataQualityEngine dataQualityEngine() {
        return new DataQualityEngine();
    }

    @Bean
    public PipelineOrchestrator pipelineOrchestrator(
        PipelineExecutionRecorder recorder,
        DataQualityEngine dataQualityEngine,
        NotificationPort notificationPort,
        @Value("${alphagraph.data-quality.floor:0.7}") double qualityFloor
    ) {
        return new PipelineOrchestrator(recorder, dataQualityEngine, notificationPort, qualityFloor);
    }
}
