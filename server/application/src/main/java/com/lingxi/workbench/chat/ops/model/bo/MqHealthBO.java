package com.lingxi.workbench.chat.ops.model.bo;

import java.time.OffsetDateTime;

/**
 * @param available RocketMQ与事务Topic当前是否可查询
 * @param message   安全状态说明
 * @param checkedAt 检查时间
 */
public record MqHealthBO(boolean available, String message, OffsetDateTime checkedAt) {
}
