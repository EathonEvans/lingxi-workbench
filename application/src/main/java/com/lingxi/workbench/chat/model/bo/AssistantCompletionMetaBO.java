package com.lingxi.workbench.chat.model.bo;

/**
 * Assistant 完成时写入的模型与收口信息。
 *
 * @param modelInstanceId 最终模型实例主键
 * @param code            非完整收口原因编码，完整回答为空
 * @param message         面向用户的收口说明，完整回答为空
 */
public record AssistantCompletionMetaBO(Long modelInstanceId, String code, String message) {

    public static AssistantCompletionMetaBO completed(Long modelInstanceId) {
        return new AssistantCompletionMetaBO(modelInstanceId, null, null);
    }

    public static AssistantCompletionMetaBO partial(String code, String message) {
        return new AssistantCompletionMetaBO(null, code, message);
    }
}
