package com.lingxi.workbench.infra.ai.tool;

/** Tool 已保存不可变检查点并等待人工决定，调用线程应立即释放。 */
public class HumanInteractionRequiredException extends RuntimeException {

    private final Long interactionId;

    public HumanInteractionRequiredException(Long interactionId) {
        super("Tool执行等待人工审批，interactionId=" + interactionId);
        this.interactionId = interactionId;
    }

    public Long interactionId() {
        return interactionId;
    }
}
