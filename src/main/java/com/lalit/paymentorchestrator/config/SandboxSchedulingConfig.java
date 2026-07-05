package com.lalit.paymentorchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SandboxSchedulingConfig {

    @Bean
    public TaskScheduler sandboxTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("sandbox-auto-finalize-");
        taskScheduler.initialize();
        return taskScheduler;
    }
}
