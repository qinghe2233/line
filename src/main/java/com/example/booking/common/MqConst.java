package com.example.booking.common;

/**
 * MQ 常量集中管理，避免 topic 字符串散落在各处写错。
 */
public final class MqConst {

    private MqConst() {
    }

    /** 订单相关（超时关单、状态变更） */
    public static final String ORDER_TOPIC = "booking-order-topic";
    public static final String ORDER_TAG_TIMEOUT_CLOSE = "TIMEOUT_CLOSE";

    /** 通知相关 */
    public static final String NOTIFY_TOPIC = "booking-notify-topic";
    public static final String NOTIFY_TAG_APPOINTMENT_CREATED = "APPOINTMENT_CREATED";
    public static final String NOTIFY_TAG_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";

    /** 排队叫号：叫号后 3 分钟未到自动过号 */
    public static final String QUEUE_TOPIC = "booking-queue-topic";
    public static final String QUEUE_TAG_CALL_TIMEOUT = "CALL_TIMEOUT";

    /** 消费组 */
    public static final String CONSUMER_GROUP_ORDER_TIMEOUT = "booking-order-timeout-consumer";
    public static final String CONSUMER_GROUP_QUEUE_CALL_TIMEOUT = "booking-queue-call-timeout-consumer";

    /**
     * RocketMQ 4.x/5.x 的延时等级是固定档位：
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h。
     * 没有 15 分钟档，所以用 10 分钟（14 档）先投递一次，
     * 消费端发现未到 pay_deadline 时再补投 5 分钟（9 档），合计正好 15 分钟。
     */
    public static final int DELAY_LEVEL_10_MIN = 14;
    public static final int DELAY_LEVEL_5_MIN = 9;
    /** 3 分钟档（第 7 档），用于叫号后 3 分钟自动过号 */
    public static final int DELAY_LEVEL_3_MIN = 7;
}
