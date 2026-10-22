package edu.unsw.core.constant;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:44
 * @description Request path prefix, used to identify different identity modules
 */
public final class RedisKeyConstant {
    private RedisKeyConstant() {

    }

    public static final String TOKEN_KEY_PREFIX = "auth:token:";

    public static final String SLOT_LOCK_PREFIX = "lock:slot:";

    public static final String SSE_TICKET_PREFIX = "sse:ticket:";
    public static final String SSE_DISCONNECT_CHANNEL = "TOPIC_SSE_DISCONNECT";
    public static final String SSE_NOTIFY_CHANNEL = "TOPIC_SSE_NOTIFICATION";

    public static final String MESSAGE_STREAM_KEY = "message-stream";
    public static final String MESSAGE_STREAM_PAYLOAD_KEY = "payload";
    public static final String MESSAGE_STREAM_GROUP_SAVE = "group:message:save";

    public static final String COMING_EVENT_QUEUE_KEY = "coming_event_queue";
    public static final String COMING_EVENT_SCHEDULER_LOCK_KEY = "lock:coming_event_scheduler";

    public static final String COMPLETE_EVENT_REMINDER_SCHEDULER_LOCK_KEY = "lock:complete_event_reminder_scheduler";
}
