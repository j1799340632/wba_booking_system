package edu.unsw.core.config.redisson;

import edu.unsw.core.utils.RedissonUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RStream;
import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.stream.StreamTrimArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 18/7/2026 17:10
 * @description Redis Stream Consumer Group Common Abstract Base Class
 */
public abstract class AbstractStreamConsumer<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * State atomic locks ensure that consumer threads are only started once during their entire lifecycle, preventing initial overlap of concurrency
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * A switch to control whether a long polling loop continues
     */
    private volatile boolean running = true;

    /**
     * Distributed cursor: The starting physical message pointer position in Redis when the next autoClaim scan is performed
     */
    private StreamMessageId nextAutoClaimId = StreamMessageId.MIN;

    /**
     * The number of undelivered new messages retrieved each time (single message preemption, to prevent node deadlock)
     */
    private static final int NEW_MESSAGE_BATCH_SIZE = 1;

    /**
     * Batch size for processing the current instance's own backlog of Pending messages
     */
    private static final int OWN_PENDING_BATCH_SIZE = 10;

    /**
     * Batch size for each claiming of other failed instances pending messages
     */
    private static final int CLAIM_PENDING_BATCH_SIZE = 10;

    /**
     * Record the timestamp of the last time other failed instances were checked
     */
    private long lastClaimTime = 0L;

    /**
     * Frequency limit for checking other failed instances
     */
    private static final long CLAIM_INTERVAL_MS = 10000L;

    /**
     * Long polling network blocking hang timeout for pulling new messages
     */
    private static final Duration NEW_MESSAGE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * The idle threshold for determining when other instances' messages enter "offline state" (if no ACK is received within 30 seconds, preemption is allowed)
     */
    private static final Duration IDLE_PENDING_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Fault recovery cooling interval when low-level infinite loop long polling causes severe network jitter
     */
    private static final Duration ERROR_RETRY_INTERVAL = Duration.ofSeconds(5);

    /**
     * The maximum expected message retention audit limit for a Stream queue in Redis
     */
    private static final int STREAM_MAX_LEN = 10000;

    /**
     * The number of items triggered by trimming (a trim operation is triggered once for every 100 successfully consumed items)
     */
    private static final int TRIM_AFTER_CONSUMED_COUNT = 1000;

    /**
     * The time window for triggering pruning (at least one physical memory pruning check every 30 seconds)
     */
    private static final Duration TRIM_INTERVAL = Duration.ofHours(1);

    /**
     * Since the last physical pruning, the current instance's cumulative message consumption counter
     */
    private int consumedSinceLastTrim = 0;

    /**
     * The absolute timestamp of the last time physical clipping was triggered
     */
    private long lastTrimTime = System.currentTimeMillis();

    private final RedissonUtils redissonUtils;
    private final ExecutorService executorService;
    private final String streamKey;
    private final String payloadKey;
    private final String groupName;
    private final String consumerName;

    protected AbstractStreamConsumer(String streamKey, String payloadKey, String groupName, RedissonUtils redissonUtils, Environment environment) {
        this.redissonUtils = redissonUtils;
        this.streamKey = streamKey;
        this.payloadKey = payloadKey;
        this.groupName = groupName;
        this.executorService = Executors.newSingleThreadExecutor(new StreamConsumerThreadFactory(streamKey));

        String hostIdent;
        try {
            // Get IP
            String localIp = InetAddress.getLocalHost().getHostAddress();

            // Get port
            String port = environment.getProperty("local.server.port");
            if (port == null) {
                // If not completely running, read configuration
                port = environment.getProperty("server.port", "8080");
            }

            hostIdent = localIp + ":" + port;
        } catch (Exception e) {
            hostIdent = UUID.randomUUID().toString().substring(0, 8);
            logger.warn("Cannot read instance's IP or port, consumerName is: {}", hostIdent, e);
        }
        this.consumerName = "node-" + hostIdent;
    }

    /**
     * Listen to stream message.
     *
     * @param messageBody data
     */
    protected abstract void onMessage(T messageBody);

    @EventListener(ApplicationReadyEvent.class)
    public void initAndStartConsume() {
        // Using CAS atomic locks to forcibly isolate multi-stage initialization and prevent threads from being repeatedly started
        if (!started.compareAndSet(false, true)) {
            logger.warn("Stream consumer for [{}] has already started, duplicate start ignored.", streamKey);
            return;
        }

        try {
            redissonUtils.createStreamGroup(streamKey, groupName);
            logger.info("Successfully created Stream Consumer Group: [{}] for Stream: [{}]", groupName, streamKey);
        } catch (Exception e) {
            if (isConsumerGroupAlreadyExists(e)) {
                logger.info("Stream Consumer Group [{}] for Stream [{}] already exists.", groupName, streamKey);
            } else {
                started.set(false);
                logger.error("Failed to create Stream Consumer Group [{}] for Stream [{}].", groupName, streamKey, e);
                throw e;
            }
        }

        executorService.submit(this::loopConsume);
    }

    private boolean isConsumerGroupAlreadyExists(Exception e) {
        String message = e.getMessage();
        return message != null && message.contains("BUSYGROUP");
    }

    private void loopConsume() {
        RStream<String, Object> stream = redissonUtils.getStream(streamKey);
        logger.info("Stream consumer [{}] started listening to [{}].", consumerName, streamKey);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Handling pending messages after restarting
                consumeOwnPendingMessages(stream);

                // Periodically retrieve pending messages from other failed instances
                long now = System.currentTimeMillis();
                if (now - lastClaimTime >= CLAIM_INTERVAL_MS) {
                    // Handling pending messages from other failed instances
                    claimIdlePendingMessages(stream);
                    lastClaimTime = System.currentTimeMillis();
                }

                // Handle new message
                consumeNewMessages(stream);

            } catch (Exception e) {
                if (!running || isInterruptedException(e) || Thread.currentThread().isInterrupted()) {
                    logger.info("Stream consumer [{}] received shutdown signal during polling, exiting loop gracefully...", consumerName);
                    Thread.currentThread().interrupt();
                    break;
                }

                logger.error("[Stream polling error] retrying after {} ms.", ERROR_RETRY_INTERVAL.toMillis(), e);
                sleepQuietly(ERROR_RETRY_INTERVAL);
            }
        }

        logger.info("Stream consumer [{}] stopped listening to [{}].", consumerName, streamKey);
    }

    private boolean isInterruptedException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof InterruptedException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Re-consume pending messages already delivered to this consumer but not ACKed.
     */
    private void consumeOwnPendingMessages(RStream<String, Object> stream) {
        StreamReadGroupArgs args = StreamReadGroupArgs.greaterThan(new StreamMessageId(0, 0))
                .count(OWN_PENDING_BATCH_SIZE)
                .timeout(Duration.ZERO);

        Map<StreamMessageId, Map<String, Object>> readResult = stream.readGroup(groupName, consumerName, args);

        if (readResult != null && !readResult.isEmpty()) {
            consumeReadResult(stream, readResult, "own-pending");
            sleepQuietly(Duration.ofMillis(1000));
        }
    }

    /**
     * Claim idle pending messages from other consumers, then consume them as current consumer.
     */
    private void claimIdlePendingMessages(RStream<String, Object> stream) {
        AutoClaimResult<String, Object> claimResult = stream.autoClaim(
                groupName,
                consumerName,
                IDLE_PENDING_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS,
                nextAutoClaimId,
                CLAIM_PENDING_BATCH_SIZE
        );

        if (claimResult == null) {
            return;
        }

        // Dynamic sliding pointer design realizes smooth batch iteration of the Pending implicit linked list, with automatic closed-loop reset at the end
        nextAutoClaimId = claimResult.getNextId() == null ? StreamMessageId.MIN : claimResult.getNextId();

        Map<StreamMessageId, Map<String, Object>> claimedMessages = claimResult.getMessages();
        consumeReadResult(stream, claimedMessages, "claimed-pending");
    }

    /**
     * Consume never-delivered new messages.
     */
    private void consumeNewMessages(RStream<String, Object> stream) {
        StreamReadGroupArgs args = StreamReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED).count(NEW_MESSAGE_BATCH_SIZE).timeout(NEW_MESSAGE_TIMEOUT);

        Map<StreamMessageId, Map<String, Object>> readResult = stream.readGroup(groupName, consumerName, args);

        consumeReadResult(stream, readResult, "new");
    }

    private void consumeReadResult(RStream<String, Object> stream, Map<StreamMessageId, Map<String, Object>> readResult, String source) {
        if (readResult == null || readResult.isEmpty()) {
            return;
        }

        for (Map.Entry<StreamMessageId, Map<String, Object>> entry : readResult.entrySet()) {
            StreamMessageId messageId = entry.getKey();
            Map<String, Object> messageMap = entry.getValue();

            Object payload = messageMap.get(payloadKey);

            // Defensive garbage collection mechanism. If an upstream bug causes a lack of payload, an ACK is sent decisively to prevent bad messages from remaining in the PEL and causing an infinite loop
            if (payload == null) {
                logger.warn("Stream message [{}] from [{}] has no payload, ACKing to avoid endless retry.", messageId, source);
                stream.ack(groupName, messageId);
                tryTrimStream(stream);
                continue;
            }

            try {
                @SuppressWarnings("unchecked") T body = (T) payload;

                onMessage(body);
                stream.ack(groupName, messageId);
                tryTrimStream(stream);

                logger.debug("Successfully consumed Stream message [{}] from [{}].", messageId, source);
            } catch (Exception bizEx) {
                logger.error("[Stream business consumption failed] message [{}] from [{}] remains pending.", messageId, source, bizEx);
            }
        }
    }

    /**
     * A two-pronged approach of "batch production + time window" to avoid overly strict cost-cutting
     *
     * @param stream Redisson stream
     */
    private void tryTrimStream(RStream<String, Object> stream) {
        consumedSinceLastTrim++;

        long now = System.currentTimeMillis();
        boolean countReached = consumedSinceLastTrim >= TRIM_AFTER_CONSUMED_COUNT;
        boolean intervalReached = now - lastTrimTime >= TRIM_INTERVAL.toMillis();

        // As long as any one of the defense lines is met (100 lines are reached or 30 seconds have elapsed), physical scaling down will be allowed
        if (!countReached && !intervalReached) {
            return;
        }

        try {
            stream.trimNonStrict(StreamTrimArgs.maxLen(STREAM_MAX_LEN).limit(TRIM_AFTER_CONSUMED_COUNT));

            // Reset counters and time anchors
            consumedSinceLastTrim = 0;
            lastTrimTime = now;
        } catch (Exception e) {
            logger.warn("Failed to trim Stream [{}], will retry later.", streamKey, e);
        }
    }

    /**
     * Fault-tolerant cooling prevents the system from spinning out of control when an error occurs
     *
     * @param duration Sleep time
     */
    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void close() {
        logger.info("Shutting down Stream consumer for [{}].", streamKey);
        running = false;

        if (executorService != null && !executorService.isShutdown()) {
            // Refuse new tasks
            executorService.shutdown();
            try {
                // Wait 3 seconds for existing tasks to complete
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Stream consumer for [{}] did not terminate gracefully within 3 seconds, forcing shutdown.", streamKey);
                    executorService.shutdownNow();
                }
            } catch (InterruptedException _) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private record StreamConsumerThreadFactory(String streamKey) implements ThreadFactory {
        @Override
        public Thread newThread(@Nonnull Runnable runnable) {
            Thread thread = new Thread(runnable, "RedisStreamConsumer-" + streamKey);
            // Must be an independent, non-daemon thread to prevent it from being mistakenly killed by the JVM
            thread.setDaemon(false);
            return thread;
        }
    }
}