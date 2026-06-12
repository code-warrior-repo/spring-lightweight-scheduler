package org.codewarrior.scheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private final String projectName;

    public SchedulerConfig(@Value("${spring.application.name}") String projectName) {
        this.projectName = projectName;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${scheduler.taskScheduler.poolSize:10}") int poolSize,
            @Value("${scheduler.taskScheduler.awaitTerminationSeconds:30}") int awaitTerminationSeconds) {

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix((projectName != null ? projectName : "dynamic") + "-job-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        scheduler.initialize();
        return scheduler;
    }
}