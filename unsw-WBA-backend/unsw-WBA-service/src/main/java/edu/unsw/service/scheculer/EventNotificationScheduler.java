package edu.unsw.service.scheculer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.NotificationDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.service.SseService;
import edu.unsw.service.enums.AssessmentBookingStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 30/7/2026 22:05
 * @description EventNotificationScheduler (e.g., bookings are coming...)
 */
@Component
public class EventNotificationScheduler {
    private final Logger logger = LoggerFactory.getLogger(EventNotificationScheduler.class);

    private final RedissonUtils redissonUtils;
    private final AssessmentBookingMapper assessmentBookingMapper;
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final SseService sseService;
    private final UserMapper userMapper;

    public EventNotificationScheduler(RedissonUtils redissonUtils, AssessmentBookingMapper assessmentBookingMapper, AssessmentSlotMapper assessmentSlotMapper, SseService sseService, UserMapper userMapper) {
        this.redissonUtils = redissonUtils;
        this.assessmentBookingMapper = assessmentBookingMapper;
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.sseService = sseService;
        this.userMapper = userMapper;
    }

    @Scheduled(cron = "0 0 8,17 * * ?")
    public void scheduleComingEventReminders() {
        boolean isLocked = redissonUtils.tryLock(RedisKeyConstant.COMING_EVENT_SCHEDULER_LOCK_KEY, 0, 600);
        if (!isLocked) {
            throw new BusinessException(ResponseEnum.LOCK_CONTROL);
        }

        try {
            logger.info("Begin do upcoming bookings scan task...");

            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            // Scan for unannounced events that are scheduled to begin within the next 34 hours (No messages will be missed)
            ZonedDateTime windowEnd = now.plusHours(34);

            List<AssessmentBooking> assessmentBookingList = assessmentBookingMapper.selectList(
                    Wrappers.<AssessmentBooking>lambdaQuery()
                            .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.CONFIRMED)
                            .le(AssessmentBooking::getStartTime, windowEnd)
            );

            if (assessmentBookingList.isEmpty()) {
                return;
            }

            logger.info("{} bookings were found that started within the last 24 hours", assessmentBookingList.size());

            for (AssessmentBooking booking : assessmentBookingList) {
                Long bookingId = booking.getId();
                try {
                    // Calculate the delay time: the millisecond difference between the current moment and 24 hours before the event starts, automatically handling daylight saving time
                    ZonedDateTime notifyTime = booking.getStartTime().minusHours(24).atZone(ZoneId.systemDefault());
                    long delayMillis = Duration.between(now, notifyTime).toMillis();

                    if (delayMillis <= 0) {
                        // If the calculated time is less than or equal to 0 (meaning the trigger point has just passed or has already passed), set the delay to 0 and trigger immediately
                        delayMillis = 0;
                    }

                    // Post the eventId to the delayed queue and remove duplicates
                    redissonUtils.addToDelayedQueueWithDeduplication(
                            RedisKeyConstant.COMING_EVENT_QUEUE_KEY,
                            bookingId,
                            String.valueOf(bookingId),
                            delayMillis,
                            TimeUnit.MILLISECONDS
                    );
                } catch (Exception e) {
                    logger.error("Delivery delay notification failed, bookingId: {}", bookingId, e);
                }
            }
        } finally {
            redissonUtils.unlock(RedisKeyConstant.COMING_EVENT_SCHEDULER_LOCK_KEY);
        }
    }

    @Scheduled(cron = "0 30 12 * * ?")
    public void scheduleCompleteEventReminders() {
        boolean isLocked = redissonUtils.tryLock(RedisKeyConstant.COMPLETE_EVENT_REMINDER_SCHEDULER_LOCK_KEY, 0, 600);
        if (!isLocked) {
            throw new BusinessException(ResponseEnum.LOCK_CONTROL);
        }

        try {
            logger.info("Begin do complete assessments reminder scan task...");

            List<AssessmentBooking> assessmentBookingList = assessmentBookingMapper.selectList(
                    Wrappers.<AssessmentBooking>lambdaQuery()
                            .eq(AssessmentBooking::getStatus, AssessmentBookingStatusEnum.CONFIRMED)
                            .le(AssessmentBooking::getEndTime, LocalDateTime.now(ZoneId.systemDefault()))
            );

            if (assessmentBookingList.isEmpty()) {
                return;
            }

            logger.info("{} ended bookings were found", assessmentBookingList.size());

            Set<Long> slotIdSet = assessmentBookingList.stream().map(AssessmentBooking::getAssessmentSlotId).collect(Collectors.toSet());


            List<AssessmentSlot> assessmentSlotList = assessmentSlotMapper.selectList(
                    Wrappers.<AssessmentSlot>lambdaQuery()
                            .in(AssessmentSlot::getId, slotIdSet)
            );

            if (assessmentSlotList.isEmpty()) {
                return;
            }

            assessmentSlotList.forEach(slot -> {
                User supervisor = userMapper.selectOne(
                        Wrappers.<User>lambdaQuery()
                                .eq(User::getId, slot.getCreator())
                );

                if (Objects.isNull(supervisor)) {
                    return;
                }

                // Send notifications
                NotificationDto notificationDtoToSupervisor = new NotificationDto();
                notificationDtoToSupervisor.setSenderId(0L);
                notificationDtoToSupervisor.setIsSendEmail(true);
                notificationDtoToSupervisor.setEventName("Remember to complete the assessment");
                notificationDtoToSupervisor.setEventContent("""
                Hi, %s
                
                You have an assessment with an end time. Please go upcoming session page, click "complete" and rate it.
                """.formatted(supervisor.getName()));
                notificationDtoToSupervisor.setReceiverId(supervisor.getId());
                sseService.sendNotification(notificationDtoToSupervisor);
            });
        } finally {
            redissonUtils.unlock(RedisKeyConstant.COMPLETE_EVENT_REMINDER_SCHEDULER_LOCK_KEY);
        }
    }
}
