package com.lingxi.workbench.application.runtime.service.trigger;

/** 定时触发器 MQ 常量；命名沿用 lingxi-<域>-<用途> 约定。 */
public final class TriggerMqConstants {

    /** 到期触发投递主题。 */
    public static final String TRIGGER_FIRE_TOPIC = "lingxi-trigger-fire";

    /** 到期触发消费者组。 */
    public static final String TRIGGER_FIRE_CONSUMER_GROUP = "lingxi-trigger-fire-consumer";

    /** 有限重试上限，与其他消费者保持一致。 */
    public static final int MAX_RECONSUME_TIMES = 5;

    private TriggerMqConstants() {
    }
}
