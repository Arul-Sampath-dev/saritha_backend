package com.saritha.fashions.modules.core.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Runs customer notifications (WhatsApp, email) in the background.
 *
 * Notifications are dispatched after the business operation (e.g. invoice creation)
 * has been saved, so a slow or failing provider never slows down or fails the request.
 *
 * The thread pool is owned privately by this component and NOT exposed as an
 * Executor bean on purpose: Spring Boot only creates its default applicationTaskExecutor
 * when no Executor bean exists, so exposing one would silently change other parts of
 * the app.
 */
@Component
public class NotificationDispatcher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final ThreadPoolTaskExecutor executor;

    public NotificationDispatcher() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        // If the queue is full, run on the caller thread instead of dropping the message.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
    }

    /**
     * Runs the task in the background. Exceptions thrown by the task are logged, never
     * propagated to the caller.
     */
    public void dispatch(String description, Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                log.error("Background notification '{}' failed: {}", description, exception.getMessage(), exception);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
