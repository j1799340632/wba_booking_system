package edu.unsw.core.config.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:55
 * @description Custom thread pool configuration
 */
@SpringBootConfiguration
public class ThreadPoolConfig implements AsyncConfigurer {
    private static final int CPU_PROCESSORS_SIZE = Runtime.getRuntime().availableProcessors();
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /**
     * Custom thread pool, configured with log tracking, ThreadLocal transfer, and injection of `ThreadPoolTaskExecutor threadPoolTaskExecutor` for use
     * If you use the `@Primary` annotation, you can only configure the thread pool for `@Async` by overriding `AsyncConfigure`; otherwise, `@Async` will use this pool by default.
     */
    @Bean
    @Primary
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        logger.info("--------------start myThreadPool--------------");
        logger.info("CPU threads on this machine：{}", CPU_PROCESSORS_SIZE);
        ThreadPoolTaskExecutor executor = new CustomThreadPoolTaskExecutor();
        executor.setCorePoolSize(CPU_PROCESSORS_SIZE / 2 + 1);
        executor.setMaxPoolSize(CPU_PROCESSORS_SIZE);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("myThreadPool-");
        // Set the processing policy after the task is rejected. When the poolSize has reached maxPoolSize, how to handle the new task (whether to reject or hand it over to other threads for processing), CallerRunsPolicy will execute the rejected task by the main thread after the task is rejected and added
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * Scheduled task thread pool settings
     */
    @Bean
    public TaskScheduler taskScheduler() {
        logger.info("--------------start myTaskSchedulerPool--------------");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setThreadNamePrefix("myTaskSchedulerPool-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Exception handling in asynchronous tasks
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            logger.error("Asynchronous task exception: {}", ex.getMessage(), ex);
            logger.error("Exception asynchronous method: {} -- Parameters: {}", method.getName(), params);
        };
    }

}
