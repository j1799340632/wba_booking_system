package edu.unsw.service.event;

import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/7/16 14:12
 * @description Custom event publishing
 */
@Component
public class MyEventPublish implements ApplicationEventPublisherAware {
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishEvent(ApplicationEvent applicationEvent) {
        applicationEventPublisher.publishEvent(applicationEvent);
    }

    public void publishEvent(Object object) {
        applicationEventPublisher.publishEvent(object);
    }

}
