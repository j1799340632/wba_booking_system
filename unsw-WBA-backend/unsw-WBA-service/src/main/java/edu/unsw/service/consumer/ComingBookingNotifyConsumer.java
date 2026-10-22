package edu.unsw.service.consumer;

import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.service.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 30/7/2026 22:50
 * @description Coming booking notify consumer (listen to a delay queue)
 */
@Component
public class ComingBookingNotifyConsumer implements SmartLifecycle {
    private final Logger logger = LoggerFactory.getLogger(ComingBookingNotifyConsumer.class);

    private volatile boolean running = false;

    private final ExecutorService executorService;
    private final RedissonUtils redissonUtils;
    private final SseService sseService;
    private final AssessmentBookingMapper assessmentBookingMapper;
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final UserMapper userMapper;

    public ComingBookingNotifyConsumer(RedissonUtils redissonUtils, SseService sseService, AssessmentBookingMapper assessmentBookingMapper, AssessmentSlotMapper assessmentSlotMapper, UserMapper userMapper) {
        this.redissonUtils = redissonUtils;
        this.sseService = sseService;
        this.assessmentBookingMapper = assessmentBookingMapper;
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.userMapper = userMapper;
        this.executorService = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                r -> new Thread(r, "coming-booking-queue-consumer-thread"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Override
    public void start() {
        running = true;
        executorService.submit(this::listenAndConsume);
        logger.info("The coming booking notification delay queue listener has been successfully started");
    }

    @Override
    public void stop() {
        logger.info("Stopping the coming booking notification delay queue listener...");
        running = false;
        // Interrupt the thread currently blocked on poll()
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listenAndConsume() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                redissonUtils.processDelayedQueue(RedisKeyConstant.COMING_EVENT_QUEUE_KEY, bookingId -> {
                    logger.info("Received coming booking reminder message，bookingId: {}", bookingId);
                    return processNotification((Long) bookingId);
                });
            } catch (Exception e) {
                logger.error("The coming booking notification delay queue listener consumption exception", e);
            }
        }
    }

    private boolean processNotification(Long bookingId) {
        // Deduplicated
        String consumedKey = "consumed:" + RedisKeyConstant.COMING_EVENT_QUEUE_KEY + ":" + bookingId;
        boolean isNew = redissonUtils.trySet(consumedKey, Duration.ofHours(49));

        if (!isNew) {
            return true;
        }

        try {
            AssessmentBooking booking = assessmentBookingMapper.selectById(bookingId);
            // Maybe be deleted
            if (Objects.isNull(booking)) {
                return true;
            }
            AssessmentSlot slot = assessmentSlotMapper.selectById(booking.getAssessmentSlotId());
            if (Objects.isNull(slot)) {
                return true;
            }
            User student = userMapper.selectById(booking.getCreator());
            if (Objects.isNull(student)) {
                return true;
            }
            User supervisor = userMapper.selectById(slot.getCreator());
            if (Objects.isNull(supervisor)) {
                return true;
            }

            // Send notifications
            String eventName = "Upcoming Assess";
            NotificationDto notificationDtoToStudent = new NotificationDto();
            notificationDtoToStudent.setSenderId(0L);
            notificationDtoToStudent.setIsSendEmail(true);
            notificationDtoToStudent.setEventName(eventName);
            notificationDtoToStudent.setEventContent("""
                    Hi, %s
                    
                    Your booking will begin within 24 hours.

                    Start time: %s
                    Location: %s
                    Supervisor: %s
                    """.formatted(student.getName(), booking.getStartTime(), slot.getLocation(), supervisor.getName()));
            notificationDtoToStudent.setReceiverId(student.getId());
            sseService.sendNotification(notificationDtoToStudent);

            NotificationDto notificationDtoToSupervisor = new NotificationDto();
            notificationDtoToSupervisor.setSenderId(0L);
            notificationDtoToSupervisor.setIsSendEmail(true);
            notificationDtoToSupervisor.setEventName(eventName);
            notificationDtoToSupervisor.setEventContent("""
                    Hi, %s
                    
                    You have a scheduled assessment that will begin within 24 hours.

                    Start time: %s
                    Location: %s
                    Student: %s
                    """.formatted(supervisor.getName(), booking.getStartTime(), slot.getLocation(), student.getName()));
            notificationDtoToSupervisor.setReceiverId(supervisor.getId());
            sseService.sendNotification(notificationDtoToSupervisor);
            return true;
        } catch (Exception e) {
            logger.error("Sending event notification error, bookingId: {}", bookingId, e);
            return false;
        }
    }
}
