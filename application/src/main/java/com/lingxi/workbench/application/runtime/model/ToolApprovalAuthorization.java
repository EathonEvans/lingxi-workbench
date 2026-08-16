package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;

/**
 * Tool 审批门禁结果。
 *
 * @param invocationId 实际调用意图主键，未要求审批时为空
 * @param permitted 是否已经取得执行许可
 * @param interaction 等待用户决定时展示的安全事件
 */
public record ToolApprovalAuthorization(Long invocationId, boolean permitted,
                                        ChatInteractionEventBO interaction) {

    public static ToolApprovalAuthorization unrestricted() {
        return new ToolApprovalAuthorization(null, true, null);
    }

    public static ToolApprovalAuthorization permitted(Long invocationId) {
        return new ToolApprovalAuthorization(invocationId, true, null);
    }

    public static ToolApprovalAuthorization pending(Long invocationId, ChatInteractionEventBO interaction) {
        return new ToolApprovalAuthorization(invocationId, false, interaction);
    }
}
