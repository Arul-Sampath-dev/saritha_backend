package com.saritha.fashions.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("AsyncThread-");
        executor.initialize();
        return executor;
    }

    @Scheduled(fixedRate = 60000)
    public void monitorThreadPool() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor();
        System.out.println("Thread pool stats: Active={}, Pool={}, Queue={}" +
                executor.getActiveCount()+
                executor.getPoolSize()+
                executor.getThreadPoolExecutor().getQueue().size());
    }
}
