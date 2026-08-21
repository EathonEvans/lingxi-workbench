package com.lingxi.workbench.agentruntime.event;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationStatus;
import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerStep;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 与事件类型匹配的安全载荷，不包含 Prompt、思维链、Tool 参数或原始结果。 */
public sealed interface ExecutionEventPayload permits ExecutionEventPayload.Run, ExecutionEventPayload.Agent,
        ExecutionEventPayload.Model, ExecutionEventPayload.Skill, ExecutionEventPayload.Tool,
        ExecutionEventPayload.Human, ExecutionEventPayload.Plan, ExecutionEventPayload.Step,
        ExecutionEventPayload.Clarification, ExecutionEventPayload.Failure {

    String message();

    /** @param message 可安全展示的Run生命周期说明 */
    record Run(String message) implements ExecutionEventPayload {
    }

    /**
     * @param agentId        Agent主键
     * @param agentVersionId 锁定的AgentVersion主键
     * @param message        可安全展示的说明
     */
    record Agent(Long agentId, Long agentVersionId, String message) implements ExecutionEventPayload {
    }

    /** Planner 事件保留可展示的 typed 决策，不包含 Prompt、模型原文或推理内容。 */
    @Getter
    final class Plan implements ExecutionEventPayload {

        private final int planNo;
        private final PlannerDecision decision;
        private final String message;

        public Plan(int planNo, PlannerDecision decision, String message) {
            this.planNo = planNo;
            this.decision = decision;
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    /** 计划步骤事件只携带可展示定义和终态，不包含输入与原始结果。 */
    @Getter
    final class Step implements ExecutionEventPayload {

        private final int planNo;
        private final PlannerStep step;
        private final AgentInvocationStatus status;
        private final String errorCode;
        private final String message;

        public Step(int planNo, PlannerStep step, AgentInvocationStatus status, String errorCode,
                    String message) {
            this.planNo = planNo;
            this.step = java.util.Objects.requireNonNull(step, "计划步骤不能为空");
            this.status = java.util.Objects.requireNonNull(status, "步骤状态不能为空");
            this.errorCode = errorCode;
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    /** 用户澄清事件只携带已校验的展示问题。 */
    @Getter
    final class Clarification implements ExecutionEventPayload {

        private final int planNo;
        private final PlannerClarification clarification;
        private final String message;

        public Clarification(int planNo, PlannerClarification clarification, String message) {
            this.planNo = planNo;
            this.clarification = java.util.Objects.requireNonNull(clarification, "澄清信息不能为空");
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    /**
     * @param callNo    当前Agent调用内的模型调用序号
     * @param usage     供应商返回的实际Token用量
     * @param finalCall 是否为禁用Tool的最终回答调用
     * @param message   可安全展示的说明
     */
    record Model(int callNo, TokenUsage usage, boolean finalCall, String message)
            implements ExecutionEventPayload {
        public Model {
            usage = java.util.Objects.requireNonNullElseGet(usage, TokenUsage::empty);
        }
    }

    /** 已激活 Skill 的安全审计事实。 */
    @Getter
    @Builder
    final class Skill implements ExecutionEventPayload {

        private final Long skillId;
        private final Long skillVersionId;
        private final Integer versionNo;
        private final String skillCode;
        private final String checksum;
        private final SkillActivationReason reason;
        private final List<String> effectiveToolCodes;
        private final String message;

        private Skill(Long skillId, Long skillVersionId, Integer versionNo, String skillCode,
                      String checksum, SkillActivationReason reason, List<String> effectiveToolCodes,
                      String message) {
            this.skillId = skillId;
            this.skillVersionId = skillVersionId;
            this.versionNo = versionNo;
            this.skillCode = skillCode;
            this.checksum = checksum;
            this.reason = reason;
            this.effectiveToolCodes = List.copyOf(
                    java.util.Objects.requireNonNullElseGet(effectiveToolCodes, List::of));
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    /**
     * @param toolCode       Tool稳定编码
     * @param displayName    Tool展示名称
     * @param durationMillis 已完成调用耗时
     * @param errorCode      失败时的稳定编码
     */
    record Tool(String toolCode, String displayName, Long durationMillis, String errorCode)
            implements ExecutionEventPayload {

        @Override
        public String message() {
            return displayName;
        }
    }

    /** 人工交互事件只携带安全展示事实，不包含Tool参数。 */
    @Getter
    final class Human implements ExecutionEventPayload {

        private final RuntimeInterruption interruption;
        private final String message;

        public Human(RuntimeInterruption interruption, String message) {
            this.interruption = java.util.Objects.requireNonNull(interruption, "人工中断事实不能为空");
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    /**
     * @param code    稳定失败编码
     * @param message 可安全展示的说明
     */
    record Failure(String code, String message) implements ExecutionEventPayload {
    }
}
