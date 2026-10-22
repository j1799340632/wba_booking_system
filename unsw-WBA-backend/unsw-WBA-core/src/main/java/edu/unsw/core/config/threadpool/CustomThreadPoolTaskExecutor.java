package edu.unsw.core.config.threadpool;

import edu.unsw.core.utils.ThreadWrapUtils;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:53
 * @description A subclass of ThreadPoolTaskExecutor was created, and the following operations are performed before each thread submission
 */
public class CustomThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(CustomThreadPoolTaskExecutor.class);

    private void showThreadPoolInfo(String prefix) {
        ThreadPoolExecutor threadPoolExecutor = getThreadPoolExecutor();
        log.info("{}, {}, CorePoolSize [{}], MaximumPoolSize [{}], TaskCount [{}], CompletedTaskCount [{}], ActiveCount [{}], QueueSize [{}]",
                this.getThreadNamePrefix(),
                prefix,
                threadPoolExecutor.getCorePoolSize(),
                threadPoolExecutor.getMaximumPoolSize(),
                threadPoolExecutor.getTaskCount(),
                threadPoolExecutor.getCompletedTaskCount(),
                threadPoolExecutor.getActiveCount(),
                threadPoolExecutor.getQueue().size());
    }

    @Override
    public void execute(@Nonnull Runnable task) {
        showThreadPoolInfo("Runnable do execute");
        super.execute(ThreadWrapUtils.wrap(task));
    }

    @Nonnull
    @Override
    public Future<?> submit(@Nonnull Runnable task) {
        showThreadPoolInfo("Runnable do submit");
        return super.submit(ThreadWrapUtils.wrap(task));
    }

    @Nonnull
    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
        showThreadPoolInfo("Callable do submit");
        return super.submit(ThreadWrapUtils.wrap(task));
    }

}
