package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户对服务端 clarification 事件的结构化响应。
 *
 * <p>当用户在澄清选项中点击了按钮或在槽位澄清中输入了值，前端将此对象
 * 作为 {@code ChatRequest.clarificationResponse} 字段一并提交。</p>
 *
 * <p>字段之间的关系（优先级）：</p>
 * <ol>
 *   <li>同时提供了 {@code optionCode} 和 {@code value} → 取 {@code optionCode}</li>
 *   <li>只提供了 {@code value}（自由文本槽位值）→ 取 {@code value}</li>
 *   <li>都未提供 → 使用 {@code ChatRequest.query} 作为澄清输入</li>
 * </ol>
 */
@Data
public class ClarificationResponseRequest {

    /**
     * 澄清会话 ID，前端从 clarification 事件中获取
     */
    @Pattern(regexp = "^[1-9]\\d*$", message = "澄清会话ID格式不正确")
    private String clarificationId;

    /**
     * 可选的澄清来源；缺省时保持旧意图澄清兼容行为。
     */
    @Pattern(regexp = "^(PLANNER_CLARIFICATION|RISK_CONFIRMATION)$",
            message = "澄清来源格式不正确")
    private String interactionType;

    /**
     * 选择的选项编码（歧义消解）
     */
    @Size(max = 64, message = "澄清选项编码不能超过64个字符")
    private String optionCode;

    /**
     * 自由文本值（槽位补充）
     */
    @Size(max = 2000, message = "澄清值不能超过2000个字符")
    private String value;
}
