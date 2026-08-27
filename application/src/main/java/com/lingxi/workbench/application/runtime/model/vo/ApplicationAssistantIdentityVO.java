package com.lingxi.workbench.application.runtime.model.vo;

/**
 * Application 发布版本中冻结的助手展示身份。
 *
 * @param name   助手展示名称
 * @param avatar 短期签名头像地址，可为空
 */
public record ApplicationAssistantIdentityVO(String name, String avatar) {
}
