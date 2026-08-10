package com.lingxi.workbench.chat.model.bo;

/**
 * 对话生成失败时可安全返回给客户端的信息。
 *
 * @param code    稳定错误码
 * @param message 面向用户的安全提示
 */
public record ChatErrorBO(String code, String message) {
}
