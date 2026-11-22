package com.lingxi.workbench.chat.common.constants;

/**
 * 对话生成链路的稳定错误码与安全错误文案。
 *
 * <p>错误码同时用于消息终态持久化和 SSE 客户端协议。持久化文案用于后台定位
 * 失败阶段，客户端文案只暴露可安全展示的信息，避免把模型供应商细节传给用户。</p>
 */
public interface ChatErrorConstants {

    /** 模型调用失败的稳定错误码。 */
    String MODEL_ERROR_CODE = "AI_MODEL_ERROR";

    /** 模型调用失败时保存到 Assistant 消息的内部错误文案。 */
    String MODEL_ERROR_PERSISTED_MESSAGE = "模型服务调用失败";

    /** 模型调用失败时通过 SSE 返回给用户的安全文案。 */
    String MODEL_ERROR_CLIENT_MESSAGE = "模型服务暂时不可用，请稍后重试";

    /** 模型生成任务未能启动的稳定错误码。 */
    String GENERATION_START_ERROR_CODE = "GENERATION_START_ERROR";

    /** 模型生成任务未能启动时保存到 Assistant 消息的错误文案。 */
    String GENERATION_START_ERROR_MESSAGE = "模型生成任务启动失败";

    /** 用户主动停止生成的稳定事件错误码。 */
    String GENERATION_CANCELLED_CODE = "GENERATION_CANCELLED";

    /** 用户主动停止生成时通过 SSE 返回的提示文案。 */
    String GENERATION_CANCELLED_MESSAGE = "已停止生成";
}
