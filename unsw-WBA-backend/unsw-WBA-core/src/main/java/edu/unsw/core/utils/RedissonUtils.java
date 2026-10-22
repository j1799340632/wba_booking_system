package edu.unsw.core.utils;

import org.apache.commons.io.IOUtils;
import org.redisson.api.*;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.api.queue.*;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 23:12
 * @description utility classes based on Redisson
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@Component
public class RedissonUtils {
    private final Logger logger = LoggerFactory.getLogger(RedissonUtils.class);

    private final RedissonClient redissonClient;

    public RedissonUtils(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * If a prefix processor is configured, the following method is not required manually
     */
    public NameMapper getNameMapper() {
        Config config = redissonClient.getConfig();
        return config.getNameMapper();
    }

    /**
     * Get the current client ID
     *
     * @return current client ID
     */
    public String getClientId() {
        return redissonClient.getId();
    }

    /**
     * Check if the key exists in Redis
     *
     * @param key key
     */
    public boolean hasKey(final String key) {
        return redissonClient.getBucket(key).isExists();
    }

    /**
     * Calculate the number of keys
     *
     * @param key key
     */
    public long countKey(final String key) {
        RKeys rKeys = redissonClient.getKeys();
        return rKeys.countExists(key);
    }

    /**
     * Get the list of cached basic objects
     *
     * @param pattern String regular expressions
     * @return Object list
     */
    public List<String> keys(final String pattern) {
        return redissonClient.getKeys()
                .getKeysStream(
                        KeysScanOptions.defaults()
                                .pattern(pattern)
                                // The default chunkSize is only 10! This means that if there are many keys matching this pattern in your Redis, Redisson will frequently make network interactions with the Redis server in batches, causing a surge in processing time
                                .chunkSize(5000)
                )
                .toList();
    }

    /**
     * Deleting the list of basic objects in the cache
     *
     * @param pattern String regular expressions
     * @return Number of deleted items
     */
    public long deleteByPattern(final String pattern) {
        return redissonClient.getKeys().deleteByPattern(pattern);
    }

    /**
     * List of basic objects for asynchronous cache deletion
     *
     * @param pattern String regular expressions
     * @return Number of deleted items
     */
    public RFuture<Long> deleteByPatternAsync(final String pattern) {
        return redissonClient.getKeys().deleteByPatternAsync(pattern);
    }

    /**
     * Caching basic objects such as Integer, String, and entity classes
     *
     * @param key   key
     * @param value value
     */
    public <T> void setObject(final String key, final T value) {
        setObject(key, value, false);
    }

    /**
     * Cache basic objects and retain the current object TTL validity period
     *
     * @param key       key
     * @param value     value
     * @param isSaveTtl Whether to retain the TTL validity period (e.g., if the remaining TTL is 90 seconds before setting and still 90 seconds after setting)
     */
    public <T> void setObject(final String key, final T value, final boolean isSaveTtl) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        if (isSaveTtl) {
            try {
                bucket.setAndKeepTTL(value);
            } catch (Exception _) {
                long timeToLive = bucket.remainTimeToLive();
                if (timeToLive > 0) {
                    setObject(key, value, Duration.ofMillis(timeToLive));
                } else {
                    // -1 (no expiration) or -2 (not exists): set without TTL
                    bucket.set(value);
                }
            }
        } else {
            bucket.set(value);
        }
    }

    /**
     * Cache basic objects and retain the current object TTL validity period
     *
     * @param key        key
     * @param value      value
     * @param expireTime Expiration time
     */
    public <T> void setObject(final String key, final T value, final long expireTime) {
        setObject(key, value, Duration.ofSeconds(expireTime));
    }

    /**
     * Cache basic objects and retain the current object TTL validity period
     *
     * @param key      key
     * @param value    value
     * @param duration Expiration time
     */
    public <T> void setObject(final String key, final T value, final Duration duration) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, duration);
    }

    /**
     * Register object listeners
     * The key listener needs to have `notify-keyspace-events` enabled
     *
     * @param key      key
     * @param listener Listener configuration
     */
    public <T> void addObjectListener(final String key, final ObjectListener listener) {
        RBucket<T> result = redissonClient.getBucket(key);
        result.addListener(listener);
    }

    /**
     * Get the basic object of the cache
     *
     * @param key key
     * @return value
     */
    public <T> T getObject(final String key) {
        RBucket<T> rBucket = redissonClient.getBucket(key);
        return rBucket.get();
    }

    /**
     * Batch get cached basic objects
     *
     * @param pattern Cache key regular expression
     * @return value
     */
    public <T> List<T> getObjectBatchByPattern(String pattern) {
        List<String> keys = keys(pattern);
        List<RFuture<T>> futures = new ArrayList<>();
        RBatch batch = redissonClient.createBatch();
        for (String key : keys) {
            RBucketAsync<T> bucket = batch.getBucket(key);
            RFuture<T> async = bucket.getAsync();
            futures.add(async);
        }
        // Collect result
        List<T> result = new ArrayList<>();
        batch.execute();
        futures.forEach(i -> {
            try {
                result.add(i.get());
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                logger.info("An error occurred when retrieving values", e);
            }
        });
        return result;
    }

    /**
     * Delete a single object
     *
     * @param key key
     */
    public void deleteObject(final String key) {
        redissonClient.getBucket(key).delete();
    }

    /**
     * Deleting a collection object
     *
     * @param collection keys
     */
    public <T> void deleteObject(final Collection<T> collection) {
        RBatch batch = redissonClient.createBatch();
        collection.forEach(t -> batch.getBucket(t.toString()).deleteAsync());
        batch.execute();
    }

    /**
     * Cache List data from a specified index
     *
     * @param key      key
     * @param index    Start of push index
     * @param dataList List data to be cached
     */
    public <T> void pushList(final String key, final int index, final List<T> dataList) {
        RList<T> rList = redissonClient.getList(key);
        rList.addAll(index, dataList);
    }

    /**
     * Cache List data from a specified index
     *
     * @param key   key
     * @param index Start of push index
     * @param data  List data to be cached
     */
    public <T> void pushList(final String key, final int index, final T data) {
        RList<T> rList = redissonClient.getList(key);
        rList.add(index, data);
    }

    /**
     * Cache List data from the tail of the queue
     *
     * @param key      key
     * @param dataList List data to be cached
     */
    public <T> void rPushList(final String key, final List<T> dataList) {
        RList<T> rList = redissonClient.getList(key);
        rList.addAll(dataList);
    }

    /**
     * Cache List data from the tail of the queue.
     *
     * @param key  key
     * @param data List data to be cached
     */
    public <T> void rPushList(final String key, final T data) {
        RList<T> rList = redissonClient.getList(key);
        rList.add(data);
    }

    /**
     * Cache List data from the head of the queue
     *
     * @param key      key
     * @param dataList List data to be cached
     */
    public <T> void lPushList(final String key, final List<T> dataList) {
        RList<T> rList = redissonClient.getList(key);
        rList.addAll(0, dataList);
    }

    /**
     * Cache List data from the head of the queue
     *
     * @param key  key
     * @param data List data to be cached
     */
    public <T> void lPushList(final String key, final T data) {
        RList<T> rList = redissonClient.getList(key);
        rList.add(0, data);
    }

    /**
     * Replace List data
     *
     * @param key   key
     * @param index Replace position index
     * @param data  Data to be replaced
     */
    public <T> void setList(final String key, final int index, final T data) {
        redissonClient.getList(key).fastSet(index, data);
    }

    /**
     * Register a List listener
     * The key listener needs to have `notify-keyspace-events` enabled
     *
     * @param key      key
     * @param listener Listener configuration
     */
    public <T> void addListListener(final String key, final ObjectListener listener) {
        RList<T> rList = redissonClient.getList(key);
        rList.addListener(listener);
    }

    /**
     * Get the cached RList object
     *
     * @param key key
     * @return value
     */
    public <T> RList<T> getRList(final String key) {
        return redissonClient.getList(key);
    }

    /**
     * Get the cached RList object
     *
     * @param key key
     * @return value
     */
    public <T> List<T> getList(final String key) {
        RList<T> rList = redissonClient.getList(key);
        return rList.readAll();
    }

    /**
     * Retrieve the cached list object based on the range
     *
     * @param key   key
     * @param start Acquisition begins from the date of this bid
     * @param end   Up to this index
     * @return Caching key-value pairs
     */
    public <T> List<T> getList(final String key, final int start, final int end) {
        RList<T> rList = redissonClient.getList(key);
        return rList.range(start, end);
    }

    /**
     * Retrieve the cached list object based on the range
     *
     * @param key key
     * @param end Up to this index
     * @return Caching key-value pairs
     */
    public <T> List<T> getList(final String key, final int end) {
        RList<T> rList = redissonClient.getList(key);
        return rList.range(end);
    }

    /**
     * Cache the queue from the tail
     *
     * @param key  key
     * @param data value
     */
    public <T> void pushQueue(final String key, final T data) {
        RQueue<T> queue = redissonClient.getQueue(key);
        queue.add(data);
    }

    /**
     * Cache the queue from the tail
     *
     * @param key      key
     * @param dataList values
     */
    public <T> void pushQueue(final String key, final List<T> dataList) {
        RQueue<T> queue = redissonClient.getQueue(key);
        queue.addAll(dataList);
    }

    /**
     * Retrieve elements from the queue
     *
     * @param key  key
     * @param size Number of items taken
     */
    public <T> void pollQueue(final String key, final int size) {
        RQueue<T> queue = redissonClient.getQueue(key);
        queue.poll(size);
    }

    /**
     * From the head of the cache queue
     *
     * @param key  key
     * @param data Data stored in the queue
     * @return list
     */
    @SafeVarargs
    public final <T> RDeque<T> lPushQueue(final String key, final T... data) {
        RDeque<T> queue = redissonClient.getDeque(key);
        queue.addFirst(data);
        return queue;
    }

    /**
     * From the tail buffer queue
     *
     * @param key  key
     * @param data Data stored in the queue
     * @return list
     */
    @SafeVarargs
    public final <T> RDeque<T> rPushQueue(final String key, final T... data) {
        RDeque<T> queue = redissonClient.getDeque(key);
        queue.addLast(data);
        return queue;
    }

    /**
     * Remove from the queue at the rear
     *
     * @param key key
     * @return list
     */
    public final <T> RDeque<T> rDeleteQueue(final String key) {
        RDeque<T> queue = redissonClient.getDeque(key);
        queue.removeLast();
        return queue;
    }

    /**
     * Remove from the queue head
     *
     * @param key key
     * @return list
     */
    public final <T> RDeque<T> lDeleteQueue(final String key) {
        RDeque<T> queue = redissonClient.getDeque(key);
        queue.remove();
        return queue;
    }

    /**
     * Get a blocking queue
     *
     * @param key key
     * @return blocking queue
     */
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    /**
     * Read a blocking queue
     *
     * @param key key
     * @return blocking queue
     */
    public <T> T readBlockingQueue(String key) {
        RBlockingQueue<T> blockingQueue = getBlockingQueue(key);
        try {
            return blockingQueue.take();
        } catch (InterruptedException e) {
            logger.error("An exception occurred while reading the blocking queue; the queue key: {}", key, e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Get a delay queue
     */
    @SuppressWarnings("deprecation")
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> destinationQueue) {
        return redissonClient.getDelayedQueue(destinationQueue);
    }

    /**
     * Delivery delay message
     *
     * @param queueKey queue key
     * @param value    message content
     * @param delay    delay
     * @param timeUnit time unit
     */
    @SuppressWarnings("deprecation")
    public <T> void addToDelayedQueue(String queueKey, T value, long delay, TimeUnit timeUnit) {
        RBlockingQueue<T> destinationQueue = getBlockingQueue(queueKey);
        RDelayedQueue<T> delayedQueue = getDelayedQueue(destinationQueue);

        // Messages are placed in a delayed queue, and Redisson will automatically move them to the destinationQueue after the delay expires
        delayedQueue.offer(value, delay, timeUnit);
    }

    /**
     * Delivery delay message with deduplication
     *
     * @param queueKey queue key
     * @param value    message content
     * @param deduplicationId deduplication id
     * @param delay    delay
     * @param timeUnit time unit
     */
    @SuppressWarnings("deprecation")
    public <T> void addToDelayedQueueWithDeduplication(String queueKey, T value, String deduplicationId, long delay, TimeUnit timeUnit) {
        String dedupeKey = "producer:" + queueKey + ":" + deduplicationId;

        // Try setting a flag in Redis with an expiration time equal to the delay time plus the buffer time
        // trySet is equivalent to SETNX and only returns true if the key does not exist
        RBucket<String> bucket = redissonClient.getBucket(dedupeKey);
        boolean isNew = bucket.trySet("1", 49, TimeUnit.HOURS);

        if (isNew) {
            addToDelayedQueue(queueKey, value, delay, timeUnit);
        } else {
            logger.info("This message {} is already in the delayed queue or has been delivered; skip duplicate messages", deduplicationId);
        }
    }

    /**
     * Attempt to preempt (if preemption fails, it means the message has already been successfully consumed/sent)
     *
     * @param key unique key
     * @return false represent the message has already been successfully consumed/sent
     */
    public boolean trySet(String key, Duration duration) {
        RBucket<String> bucket = redissonClient.getBucket(key);

        // Attempt to preempt (if preemption fails, it means the message has already been successfully consumed/sent)
        return bucket.setIfAbsent("PROCESSED", duration);
    }

    /**
     * News of consumption extension due
     *
     * @param queueKey  queue key
     * @param processor Business processing logic (returns true for success; false for failure, and can be resubmitted as needed)
     * @return Did the message get and process successfully?
     */
    public <T> boolean processDelayedQueue(String queueKey, Predicate<T> processor) {
        RBlockingQueue<T> destinationQueue = getBlockingQueue(queueKey);

        try {
            // Block for a maximum of 10 seconds to retrieve expired messages.
            T payload = destinationQueue.poll(10, TimeUnit.SECONDS);

            // No message within 10 seconds
            if (payload == null) {
                return false;
            }

            // Perform business processing
            boolean success = processor.test(payload);

            if (!success) {
                logger.warn("The business process failed; a retry message is being prepared for delivery, payload: {}", payload);
                // If processing fails, resubmit after a 5-minute delay for a retry
                addToDelayedQueue(queueKey, payload, 5, TimeUnit.MINUTES);
            }

            return success;
        } catch (InterruptedException e) {
            logger.warn("Consumption of DelayedQueue is interrupted, queueKey: {}, error: {}", queueKey, e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("An unknown exception occurred while consuming the DelayedQueue, queueKey: {}", queueKey, e);
            return false;
        }
    }

    /**
     * Cancel/undo a message in the delay queue
     *
     * @param queueKey queue key
     * @param value    Message object to be removed
     */
    @SuppressWarnings("deprecation")
    public <T> Boolean removeFromDelayedQueue(String queueKey, T value) {
        RBlockingQueue<T> destinationQueue = getBlockingQueue(queueKey);
        RDelayedQueue<T> delayedQueue = getDelayedQueue(destinationQueue);

        // Remove from both the delay queue and the target queue
        delayedQueue.remove(value);
        return destinationQueue.remove(value);
    }

    /**
     * Get a delayed queue
     *
     * @param key key
     * @return delayed queue
     */
    public <T> RReliableQueue<T> getReliableQueue(String key) {
        return redissonClient.getReliableQueue(key);
    }

    /**
     * Add elements to the delay queue
     *
     * @param key       delay queue key
     * @param data      Data stored in the queue
     * @param delayTime Delay time
     * @param unit      Time unit
     */
    public <T> void addToReliableQueue(String key, T data, long delayTime, TimeUnit unit) {
        RReliableQueue<T> reliableQueue = getReliableQueue(key);
        reliableQueue.add(
                QueueAddArgs.messages(
                        MessageArgs.payload(data)
                                .delay(Duration.ofMillis(unit.toMillis(delayTime)))
                )
        );
        logger.info("Add data to delay queue: {}", data);
    }

    /**
     * Add delayed messages with deduplicated IDs (suitable for idempotency and preventing duplicate notifications)
     *
     * @param key        delay queue key
     * @param data       content
     * @param messageId  Deduplicated IDs
     * @param delayTime  delay time
     * @param unit       time unit
     */
    public <T> void addToReliableQueueWithDeduplication(String key, T data, String messageId, long delayTime, TimeUnit unit) {
        RReliableQueue<T> reliableQueue = getReliableQueue(key);
        reliableQueue.add(
                QueueAddArgs.messages(
                        MessageArgs.payload(data)
                                .delay(Duration.ofMillis(unit.toMillis(delayTime)))
                                .deduplicationById(messageId, Duration.ofMillis(unit.toMillis(delayTime)))
                )
        );
        logger.info("Successfully added unique delayed message [id={}] to reliable queue [{}]", messageId, key);
    }

    /**
     * Read/pop messages from RReliableQueue
     *
     * @param key queue key
     * @return The message payload data; if the queue is empty or there are no due delayed messages, return null
     */
    public <T> T readReliableQueue(String key) {
        RReliableQueue<T> reliableQueue = getReliableQueue(key);

        // Pop-up message indicating current expiration
        QueuePollArgs queuePollArgs = new QueuePollParams().timeout(Duration.ofSeconds(10));
        Message<T> message = reliableQueue.poll(queuePollArgs);

        if (message == null) {
            return null;
        }

        try {
            T payload = message.getPayload();

            // After successful consumption, an explicit ACK must be sent to notify Redisson to completely delete the message
            reliableQueue.acknowledge(QueueAckArgs.ids(message.getId()));

            return payload;
        } catch (Exception e) {
            logger.error("An exception occurred while processing message [id={}] in reliable queue [{}]",
                    message.getId(), key, e);

            // If processing fails, a NACK rejection is issued, causing the message to be redelivered or placed in the dead-letter queue (DLQ)
            try {
                reliableQueue.negativeAcknowledge(
                        QueueNegativeAckArgs.failed(message.getId())
                                // Allow redelivery, but delay for 5 minutes before trying again (to prevent immediate retry from resulting in another error)
                                .delay(Duration.ofMinutes(5))
                );
            } catch (Exception ex) {
                logger.error("NACK operate failed", ex);
            }

            return null;
        }
    }

    /**
     * Secure consumption of RReliableQueue messages (automatic handling of ACK/NACK retries)
     *
     * @param key       queue key
     * @param processor Business processing assertion function (returns true if processing was successful and ACK is given, false if processing failed and NACK is given)
     */
    public <T> boolean processReliableQueue(String key, Predicate<T> processor) { // 👈 换成 Predicate<T>
        RReliableQueue<T> reliableQueue = getReliableQueue(key);

        // Wait 10 seconds to get message
        Message<T> message = reliableQueue.poll(
                new QueuePollParams().timeout(Duration.ofSeconds(10))
        );

        // No message within 10 seconds
        if (message == null) {
            return false;
        }

        try {
            T payload = message.getPayload();

            boolean success = processor.test(payload);

            if (success) {
                // Consume successful: ACK
                reliableQueue.acknowledge(QueueAckArgs.ids(message.getId()));
            } else {
                // Business function return false:NACK rejection triggers Redisson retry/dead letter
                reliableQueue.negativeAcknowledge(QueueNegativeAckArgs.failed(message.getId()));
            }
            return success;
        } catch (Exception e) {
            logger.error("Consume RReliableQueue failed, messageId: {}", message.getId(), e);
            try {
                // Unknown exception occurred: perform NACK rejection
                reliableQueue.negativeAcknowledge(QueueNegativeAckArgs.failed(message.getId()));
            } catch (Exception ex) {
                logger.error("NACK operate failed", ex);
            }
            return false;
        }
    }

    /**
     * Remove delayed queue data
     *
     * @param key  key
     * @param ids ids
     */
    public <T> void removeFromReliableQueue(String key, String... ids) {
        removeFromReliableQueue(getReliableQueue(key), ids);
    }

    /**
     * Remove delayed queue data
     *
     * @param reliableQueue reliableQueue
     * @param ids         Data ID to be removed
     */
    public <T> void removeFromReliableQueue(RReliableQueue<T> reliableQueue, String... ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        boolean isRemove = reliableQueue.remove(QueueRemoveArgs.ids(ids));
        if (isRemove) {
            logger.info("Data IDs removed from reliable queue [{}]: {}", reliableQueue.getName(), ids);
        } else {
            logger.error("Failed to remove data from reliable queue [{}], IDs: {}", reliableQueue.getName(), ids);
        }
    }

    /**
     * Delete ReliableQueue
     *
     * @param key key of the ReliableQueue
     */
    public void deleteReliableQueue(String key) {
        RReliableQueue<?> queue = getReliableQueue(key);
        if (queue != null && queue.isExists()) {
            queue.delete();
            logger.info("Reliable queue has been deleted, key: {}", key);
        }
    }

    /**
     * Delete the blocking queue
     *
     * @param key key of the blocking queue
     */
    public <T> void deleteBlockingQueue(String key) {
        deleteBlockingQueue(getBlockingQueue(key));
    }

    /**
     * Delete the blocking queue
     *
     * @param blockingQueue blocking queue
     */
    public <T> void deleteBlockingQueue(RBlockingQueue<T> blockingQueue) {
        if (blockingQueue == null || !blockingQueue.isExists()) {
            logger.warn("Blocking queue not exist, key: {} ", blockingQueue != null ? blockingQueue.getName() : "null");
            return;
        }
        blockingQueue.delete();
        logger.info("The blocking queue has been deleted, key: {}", blockingQueue.getName());
    }

    /**
     * Cache Set
     *
     * @param key     key
     * @param dataSet value
     * @return Was caching successful?
     */
    public <T> boolean setSet(final String key, final Set<T> dataSet) {
        RSet<T> rSet = redissonClient.getSet(key);
        return rSet.addAll(dataSet);
    }

    /**
     * Register a Set listener
     * The key listener needs to have `notify-keyspace-events` enabled
     *
     * @param key      key
     * @param listener Listener configuration
     */
    public <T> void addSetListener(final String key, final ObjectListener listener) {
        RSet<T> rSet = redissonClient.getSet(key);
        rSet.addListener(listener);
    }

    /**
     * Get the cached set
     *
     * @param key key
     * @return set object
     */
    public <T> Set<T> getSet(final String key) {
        RSet<T> rSet = redissonClient.getSet(key);
        return rSet.readAll();
    }

    /**
     * Cache Map
     *
     * @param key     key
     * @param dataMap value
     */
    public <T> void setMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null) {
            RMap<String, T> rMap = redissonClient.getMap(key);
            rMap.putAll(dataMap);
        }
    }

    /**
     * Register Map listener
     * The key listener needs to have `notify-keyspace-events` enabled
     *
     * @param key      key
     * @param listener Listener configuration
     */
    public <T> void addMapListener(final String key, final ObjectListener listener) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        rMap.addListener(listener);
    }

    /**
     * Get the cached map
     *
     * @param key key
     * @return map object
     */
    public <T> Map<String, T> getMap(final String key) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        return rMap.readAllMap();
    }

    /**
     * Get the list of keys of the cached Map
     *
     * @param key key
     * @return key list
     */
    public <T> Set<String> getMapKeySet(final String key) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        return rMap.keySet();
    }

    /**
     * Store data in Hash
     *
     * @param key   Redis key
     * @param hKey  Hash key
     * @param value Hash value
     */
    public <T> void setMapValue(final String key, final String hKey, final T value) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        rMap.put(hKey, value);
    }

    /**
     * Get data from the hash
     *
     * @param key  Redis key
     * @param hKey Hash key
     * @return Objects in a hash
     */
    public <T> T getMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        return rMap.get(hKey);
    }

    /**
     * Delete data from Hash
     *
     * @param key  Redis key
     * @param hKey Hash key
     * @return Objects in a hash
     */
    public <T> T deleteMapValue(final String key, final String hKey) {
        RMap<String, T> rMap = redissonClient.getMap(key);
        return rMap.remove(hKey);
    }

    /**
     * Get data from multiple hashes
     *
     * @param key   Redis key
     * @param hKeys Hash key list
     * @return Collection of Hash objects
     */
    public <K, V> Map<K, V> getMultiMapValue(final String key, final Set<K> hKeys) {
        RMap<K, V> rMap = redissonClient.getMap(key);
        return rMap.getAll(hKeys);
    }

    /**
     * Set valid time
     *
     * @param key     Redis key
     * @param timeout Timeout
     * @return true = setting successful; false = setting failed
     */
    public boolean expire(final String key, final long timeout) {
        return expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * Set valid time
     *
     * @param key      Redis key
     * @param duration Timeout
     * @return true = setting successful; false = setting failed
     */
    public <T> boolean expire(final String key, final Duration duration) {
        RBucket<T> rBucket = redissonClient.getBucket(key);
        return rBucket.expire(duration);
    }

    /**
     * Get the remaining lifespan of the key
     *
     * @param key key
     * @return Remaining survival time
     */
    public <T> long getTimeToLive(final String key) {
        RBucket<T> rBucket = redissonClient.getBucket(key);
        return rBucket.remainTimeToLive();
    }

    /**
     * Set atomic value
     *
     * @param key   key
     * @param value value
     */
    public void setAtomicValue(final String key, final long value) {
        RAtomicLong atomic = redissonClient.getAtomicLong(key);
        atomic.set(value);
    }

    /**
     * Get atomic value
     *
     * @param key key
     * @return value
     */
    public long getAtomicValue(final String key) {
        RAtomicLong atomic = redissonClient.getAtomicLong(key);
        return atomic.get();
    }

    /**
     * Increasing atomic value
     *
     * @param key key
     * @return value
     */
    public long incrAtomicValue(final String key) {
        RAtomicLong atomic = redissonClient.getAtomicLong(key);
        return atomic.incrementAndGet();
    }

    /**
     * Decreasing atomic value
     *
     * @param key key
     * @return value
     */
    public long decrAtomicValue(final String key) {
        RAtomicLong atomic = redissonClient.getAtomicLong(key);
        return atomic.decrementAndGet();
    }

    /**
     * Register Atomic listener
     * The key listener needs to have `notify-keyspace-events` enabled
     *
     * @param key      key
     * @param listener Listener configuration
     */
    public <T> void addAtomicListener(final String key, final ObjectListener listener) {
        RAtomicLong atomic = redissonClient.getAtomicLong(key);
        atomic.addListener(listener);
    }

    /**
     * Stream limiting
     *
     * @param key          key
     * @param rateType     Rate limiting type, per_client, means that rate limiting can be applied to each client in a cluster
     * @param rate         rate
     * @param rateInterval Rate interval, in seconds
     * @return -1 is failed
     */
    public long rateLimit(final String key, final RateType rateType, final int rate, final int rateInterval) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(rateType, rate, Duration.ofSeconds(rateInterval));
        if (rateLimiter.tryAcquire()) {
            return rateLimiter.availablePermits();
        } else {
            return -1L;
        }
    }

    /**
     * Release channel message
     *
     * @param channelKey key
     * @param msg        Data sent
     * @param consumer   Custom processing
     */
    public <T> void publish(final String channelKey, final T msg, final Consumer<T> consumer) {
        RTopic topic = redissonClient.getTopic(channelKey);
        topic.publish(msg);
        consumer.accept(msg);
    }

    /**
     * Release channel message
     *
     * @param channelKey key
     * @param msg        Data sent
     * @return the number of clients that received the message
     */
    public <T> long publish(final String channelKey, final T msg) {
        RTopic topic = redissonClient.getTopic(channelKey);
        return topic.publish(msg);
    }

    /**
     * Subscribe to receive messages
     *
     * @param channelKey key
     * @param clazz      message type
     * @param consumer   Custom processing
     * @return listenerId
     */
    public <T> int subscribe(final String channelKey, final Class<T> clazz, final Consumer<T> consumer) {
        RTopic topic = redissonClient.getTopic(channelKey);
        return topic.addListener(clazz, (channel, msg) -> consumer.accept(msg));
    }

    /**
     * Unsubscribe from receiving messages
     *
     * @param channelName key
     * @param listenerId listenerId
     */
    public void unsubscribe(String channelName, int listenerId) {
        RTopic topic = redissonClient.getTopic(channelName);
        topic.removeListener(listenerId);
    }

    /**
     * Write-on lock (with release time)
     *
     * @param lockKey     key
     * @param releaseTime Lock release time
     */
    public void lockWrite(final String lockKey, final long releaseTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        readWriteLock.writeLock().lock(releaseTime, TimeUnit.SECONDS);
    }

    /**
     * Write-on lock (with no release time)
     *
     * @param lockKey key
     */
    public void lockWrite(final String lockKey) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        readWriteLock.writeLock().lock();
    }

    /**
     * Attempting to write locks
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @return Is it locked?
     */
    public boolean tryLockWrite(final String lockKey, final long waitTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.writeLock().tryLock(waitTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempting to write locks
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public boolean tryLockWrite(final String lockKey, final long waitTime, final long releaseTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.writeLock().tryLock(waitTime, releaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempting to write locks
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        Time unit
     * @return Is it locked?
     */
    public boolean tryLockWrite(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.writeLock().tryLock(waitTime, releaseTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempting to write locks
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param unit     Time unit
     * @return Is it locked?
     */
    public boolean tryLockWrite(final String lockKey, final long waitTime, final TimeUnit unit) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.writeLock().tryLock(waitTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Read lock (with release time)
     *
     * @param lockKey     key
     * @param releaseTime Lock release time
     */
    public void lockRead(final String lockKey, final long releaseTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        readWriteLock.readLock().lock(releaseTime, TimeUnit.SECONDS);
    }

    /**
     * Read lock (no release time)
     *
     * @param lockKey key
     */
    public void lockRead(final String lockKey) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        readWriteLock.readLock().lock();
    }

    /**
     * Try reading lock
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @return Is it locked?
     */
    public boolean tryLockRead(final String lockKey, final long waitTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.readLock().tryLock(waitTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try reading lock
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public boolean tryLockRead(final String lockKey, final long waitTime, final long releaseTime) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.readLock().tryLock(waitTime, releaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try reading lock
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        Time unit
     * @return Is it locked?
     */
    public boolean tryLockRead(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.readLock().tryLock(waitTime, releaseTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try reading lock
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param unit     Time unit
     * @return Is it locked?
     */
    public boolean tryLockRead(final String lockKey, final long waitTime, final TimeUnit unit) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
        try {
            return readWriteLock.readLock().tryLock(waitTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Locked (no release time)
     *
     * @param lockKey key
     */
    public void lock(final String lockKey) {
        RLock rLock = redissonClient.getLock(lockKey);
        rLock.lock();
    }

    /**
     * Locked (with release time)
     *
     * @param lockKey     key
     * @param releaseTime Lock release time
     */
    public void lock(final String lockKey, final long releaseTime) {
        RLock rLock = redissonClient.getLock(lockKey);
        rLock.lock(releaseTime, TimeUnit.SECONDS);
    }

    /**
     * Locked (with release time)
     *
     * @param lockKey     key
     * @param releaseTime Lock release time
     * @param unit        Time unit
     */
    public void lock(final String lockKey, final long releaseTime, final TimeUnit unit) {
        RLock rLock = redissonClient.getLock(lockKey);
        rLock.lock(releaseTime, unit);
    }

    /**
     * Try locking (without releasing)
     *
     * @param lockKey key
     * @return Is it locked?
     */
    public boolean tryLock(final String lockKey, final long waitTime) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            return rLock.tryLock(waitTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try locking (without releasing)
     *
     * @param lockKey key
     * @param unit    Time unit
     * @return Is it locked?
     */
    public boolean tryLock(final String lockKey, final long waitTime, final TimeUnit unit) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            return rLock.tryLock(waitTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempt to lock (with release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public boolean tryLock(final String lockKey, final long waitTime, final long releaseTime) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            return rLock.tryLock(waitTime, releaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Attempt to lock (with release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        Time unit
     * @return Is it locked?
     */
    public boolean tryLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            return rLock.tryLock(waitTime, releaseTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try acquiring an asynchronous lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, TimeUnit.SECONDS);
    }

    /**
     * Try acquiring an asynchronous lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param unit     TimeUnit
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime, final TimeUnit unit) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, unit);
    }

    /**
     * Try using an asynchronous lock (with a release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime, final long releaseTime) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, releaseTime, TimeUnit.SECONDS);
    }

    /**
     * Try using an asynchronous lock (with a release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        Time unit
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, releaseTime, unit);
    }

    /**
     * Try using an asynchronous lock (with a release time, passing in the thread ID)
     *
     * @param lockKey         key
     * @param waitTime        Time to wait for lock acquisition
     * @param releaseTime     Lock release time
     * @param currentThreadId Thread id
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final long currentThreadId) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, releaseTime, TimeUnit.SECONDS, currentThreadId);
    }

    /**
     * Try using an asynchronous lock (with a release time, passing in the thread ID)
     *
     * @param lockKey         key
     * @param waitTime        Time to wait for lock acquisition
     * @param releaseTime     Lock release time
     * @param currentThreadId Thread id
     * @param unit            TimeUnit
     * @return Is it locked?
     */
    public Future<Boolean> tryAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit, final long currentThreadId) {
        RLock rLock = redissonClient.getLock(lockKey);
        return rLock.tryLockAsync(waitTime, releaseTime, unit, currentThreadId);
    }

    /**
     * Try applying a fair lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @return Is it locked?
     */
    public boolean tryFairLock(final String lockKey, final long waitTime) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
            return fairLock.tryLock(waitTime, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try applying a fair lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param unit     Time unit
     * @return Is it locked?
     */
    public boolean tryFairLock(final String lockKey, final long waitTime, final TimeUnit unit) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
            return fairLock.tryLock(waitTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try a fair lock (with release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public boolean tryFairLock(final String lockKey, final long waitTime, final long releaseTime) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
            return fairLock.tryLock(waitTime, releaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try a fair lock (with release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        Time unit
     * @return Is it locked?
     */
    public boolean tryFairLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        try {
            // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
            return fairLock.tryLock(waitTime, releaseTime, unit);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Try using a fair asynchronous lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, TimeUnit.SECONDS);
    }

    /**
     * Try using a fair asynchronous lock (without releasing it)
     *
     * @param lockKey  key
     * @param waitTime Time to wait for lock acquisition
     * @param unit     Time unit
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime, final TimeUnit unit) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, unit);
    }

    /**
     *Try using a fair asynchronous lock (with a release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime, final long releaseTime) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, releaseTime, TimeUnit.SECONDS);
    }

    /**
     * Try using a fair asynchronous lock (with a release time)
     *
     * @param lockKey     key
     * @param waitTime    Time to wait for lock acquisition
     * @param releaseTime Lock release time
     * @param unit        时间单位
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, releaseTime, unit);
    }

    /**
     * Try using a fair asynchronous lock (with a release time, passed in the thread ID)
     *
     * @param lockKey         key
     * @param waitTime        Time to wait for lock acquisition
     * @param releaseTime     Lock release time
     * @param currentThreadId Thread id
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final long currentThreadId) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, releaseTime, TimeUnit.SECONDS, currentThreadId);
    }

    /**
     * Try using a fair asynchronous lock (with a release time, passed in the thread ID)
     *
     * @param lockKey         key
     * @param waitTime        Time to wait for lock acquisition
     * @param releaseTime     Lock release time
     * @param unit            TimeUnit
     * @param currentThreadId Thread id
     * @return Is it locked?
     */
    public Future<Boolean> tryFairAsyncLock(final String lockKey, final long waitTime, final long releaseTime, final TimeUnit unit, final long currentThreadId) {
        RLock fairAsyncLock = redissonClient.getFairLock(lockKey);
        // Attempting to lock, it will wait a maximum of waitTime seconds. After locking, it will automatically unlock after releaseTime seconds
        return fairAsyncLock.tryLockAsync(waitTime, releaseTime, unit, currentThreadId);
    }

    /**
     * Release lock
     *
     * @param lockKey key
     * @return Was the release successful?
     */
    public boolean unlock(final String lockKey) {
        RLock rLock = redissonClient.getLock(lockKey);
        if (rLock.isLocked() && rLock.isHeldByCurrentThread()) {
            rLock.unlock();
            return true;
        }
        return false;
    }

    /**
     * Initialize Bloom filter
     *
     * @param key           key
     * @param expectedCount Predicted number of insertions
     */
    public <T> void initBloomFilter(String key, long expectedCount) {
        initBloomFilter(key, expectedCount, 0.01);
    }

    /**
     * Initialize Bloom filter
     *
     * @param key             key
     * @param expectedCount   Predicted number of insertions
     * @param misjudgmentRate False positive rate
     */
    public <T> void initBloomFilter(String key, long expectedCount, double misjudgmentRate) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(key);
        // Initialize the Bloom filter. The first parameter is the number of predicted insertions, and the second parameter is the false positive rate. Higher accuracy results in higher memory consumption
        bloomFilter.tryInit(expectedCount, misjudgmentRate);
    }

    /**
     * Adding elements to a Bloom filter
     *
     * @param key   key
     * @param value value
     */
    public <T> void addBloomFilter(String key, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(key);
        bloomFilter.add(value);
    }

    /**
     * Adding elements to a Bloom filter
     *
     * @param key   key
     * @param valueList values
     */
    public <T> void addBloomFilter(String key, List<T> valueList) {
        // You need to check if it's empty; otherwise, the add method will throw an error
        if (CollectionUtils.isEmpty(valueList)) {
            return;
        }
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(key);
        bloomFilter.add(valueList);
    }

    /**
     * Determining whether an element exists in a Bloom filter can only definitively conclude that it is not present, not that it is
     *
     * @param key   key
     * @param value value
     * @return Is exists?
     */
    public <T> boolean isBloomFilterContain(String key, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(key);
        return bloomFilter.contains(value);
    }

    private String readLuaString(File luaFile, InputStream inputStream) {
        try {
            if (luaFile == null) {
                return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            } else {
                return Files.readString(luaFile.toPath());
            }
        } catch (IOException e) {
            logger.error("Failed to read Lua script file", e);
            return null;
        }
    }

    /**
     * Execute Lua scripts
     *
     * @param inputStream Lua scripts file stream
     * @param mode Script read/write mode
     * @param returnType Script execution return result type
     * @param keyList Script parameters key
     * @param valueArray Script parameter values
     * @return Script execution return result
     */
    public Object executeLua(InputStream inputStream, RScript.Mode mode, RScript.ReturnType returnType, List<Object> keyList, Object... valueArray) {
        String luaString = readLuaString(null, inputStream);
        return executeLua(luaString, mode, returnType, keyList, valueArray);
    }

    /**
     * Execute Lua scripts
     *
     * @param luaFile Lua scripts file object
     * @param mode Script read/write mode
     * @param returnType Script execution return result type
     * @param keyList Script parameters key
     * @param valueArray Script parameter values
     * @return Script execution return result
     */
    public Object executeLua(File luaFile, RScript.Mode mode, RScript.ReturnType returnType, List<Object> keyList, Object... valueArray) {
        String luaString = readLuaString(luaFile, null);
        return executeLua(luaString, mode, returnType, keyList, valueArray);
    }

    /**
     * Execute Lua scripts
     *
     * @param luaString Lua scripts content
     * @param mode Script read/write mode
     * @param returnType Script execution return result type
     * @param keyList Script parameters key
     * @param valueArray Script parameter values
     * @return Script execution return result
     */
    public Object executeLua(String luaString, RScript.Mode mode, RScript.ReturnType returnType, List<Object> keyList, Object... valueArray) {
        if (luaString == null) {
            return null;
        }
        // The `eval` method sends script code to the server every time, which consumes network bandwidth. When extreme performance is required, script caching should be used instead
        RScript rScript = redissonClient.getScript();
        return rScript.eval(mode, luaString, returnType, keyList, valueArray);
    }

    /**
     * Execute scripts cached on the server
     *
     * @param luaSha Lua scripts file sha1
     * @param mode Script read/write mode
     * @param returnType Script execution return result type
     * @param keyList Script parameters key
     * @param valueArray Script parameter values
     * @return Script execution return result
     */
    public Object executeLuaSha(String luaSha, RScript.Mode mode, RScript.ReturnType returnType, List<Object> keyList, Object... valueArray) {
        RScript rScript = redissonClient.getScript();
        return rScript.evalSha(mode, luaSha, returnType, keyList, valueArray);
    }

    /**
     * Execute scripts cached on the server
     *
     * @param luaString Lua scripts content
     * @return Lua scripts file sha1
     */
    public String cacheLua(String luaString) {
        if (luaString == null) {
            return null;
        }
        return redissonClient.getScript().scriptLoad(luaString);
    }

    /**
     * Cache the script on the server and get the script's SHA1 value
     *
     * @param luaFile Lua scripts file object
     * @return Lua scripts file sha1
     */
    public String cacheLua(File luaFile) {
        String luaString = readLuaString(luaFile, null);
        return cacheLua(luaString);
    }

    /**
     * Cache the script on the server and get the script's SHA1 value
     *
     * @param inputStream Lua scripts file stream
     * @return Lua scripts file sha1
     */
    public String cacheLua(InputStream inputStream) {
        String luaString = readLuaString(null, inputStream);
        return cacheLua(luaString);
    }

    /**
     * Clear server script cache
     */
    public void flushAllScript() {
        redissonClient.getScript().scriptFlush();
    }

    /**
     * Get a Redisson stream
     *
     * @param streamKey stream key
     * @return RStream
     */
    public <T> RStream<String, T> getStream(String streamKey) {
        return redissonClient.getStream(streamKey);
    }

    /**
     * Create a stream group
     *
     * @param streamKey stream key
     * @param groupName group name
     */
    public <T> void createStreamGroup(String streamKey, String groupName) {
        RStream<String, T> stream = redissonClient.getStream(streamKey);
        stream.createGroup(
                StreamCreateGroupArgs
                        .name(groupName)
                        .id(StreamMessageId.ALL)
                        .makeStream()
        );
    }

    /**
     * Add data to a stream
     *
     * @param streamKey stream key
     * @param dataKey payload key
     * @param data data
     */
    public <T> void addToStream(String streamKey, String dataKey, T data) {
        RStream<String, T> stream = redissonClient.getStream(streamKey);
        Map<String, T> payloadMap = Map.of(dataKey, data);
        stream.add(StreamAddArgs.entries(payloadMap));
    }

}
