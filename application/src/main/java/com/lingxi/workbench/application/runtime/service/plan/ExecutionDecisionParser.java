package com.lingxi.workbench.application.runtime.service.plan;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 将 Planner 的纯 JSON 输出解析为受控执行决策。
 * <p>
 * 解析过程设置两层防线：先对 JSON 逐层做字段白名单校验（decision/plan/step/inputs/clarification
 * 五级结构各有独立的允许字段集合），再做结构类型校验（对象、数组及元素类型），
 * 确保未声明字段与畸形结构在进入执行链路前被拦截，并统一以 409 协议错误抛出。
 */
@Component
public class ExecutionDecisionParser {

    private static final Set<String> DECISION_FIELDS = Set.of("action", "capabilityCode", "plan", "clarification");
    private static final Set<String> PLAN_FIELDS = Set.of("title", "steps");
    private static final Set<String> STEP_FIELDS = Set.of("id", "name", "capabilityCode", "dependsOn", "inputs");
    private static final Set<String> INPUT_FIELDS = Set.of("name", "source", "stepId", "value");
    private static final Set<String> CLARIFICATION_FIELDS = Set.of(
            "question", "options", "interactionType", "capabilityCodes");

    /**
     * 解析 Planner 输出的 JSON 为执行决策，作为执行链路的入口。
     * <p>
     * 只接受单个非空 JSON 对象；Markdown 代码块、数组等封装形式以及任何未声明字段均视为协议错误。
     * 白名单与结构校验通过后由 fastjson 绑定为 {@link ExecutionDecision}。
     *
     * @param output Planner 返回的原始 JSON 对象
     * @return 通过白名单与结构校验后的执行决策
     * @throws BusinessException 内容为空、字段越界或结构非法时抛出 409 协议错误
     */
    public ExecutionDecision parse(JSONObject output) {
        // 空输出视为协议错误：Planner 必须返回非空 JSON 对象
        if (output == null || output.isEmpty()) {
            throw invalid("Planner返回内容为空");
        }
        // 区分两类异常：白名单/结构校验失败抛出带具体原因的 BusinessException 直接上抛；
        // 而 toJavaObject 绑定阶段的结构不匹配（IllegalArgumentException）统一转换为通用协议错误
        try {
            requireKnownFields(output, DECISION_FIELDS, "决策");
            validatePlanShape(output.get("plan"));
            validateClarificationShape(output.get("clarification"));
            return output.toJavaObject(ExecutionDecision.class);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid("Planner返回的JSON结构无效");
        }
    }

    /**
     * 校验 plan 字段的结构：必须为 JSON 对象且字段命中 plan 白名单。
     * <p>
     * plan 为可选字段，为 null 时直接放行；steps 为可选数组，存在时逐个校验每个 step
     * 的字段白名单，并递归校验 step.inputs 的结构。
     *
     * @param value plan 字段的原始值
     */
    private static void validatePlanShape(Object value) {
        // plan 为可选字段：未提供时跳过校验，后续按缺省语义处理
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONObject plan)) {
            throw invalid("plan必须是JSON对象");
        }
        requireKnownFields(plan, PLAN_FIELDS, "plan");
        Object stepsValue = plan.get("steps");
        // steps 同样为可选字段，未提供时直接放行
        if (stepsValue == null) {
            return;
        }
        if (!(stepsValue instanceof JSONArray steps)) {
            throw invalid("plan.steps必须是数组");
        }
        // 逐步骤校验：每个 step 必须是 JSON 对象、字段命中白名单，并递归校验其 inputs
        for (Object stepValue : steps) {
            if (!(stepValue instanceof JSONObject step)) {
                throw invalid("plan.steps中的元素必须是JSON对象");
            }
            requireKnownFields(step, STEP_FIELDS, "plan.step");
            validateInputsShape(step.get("inputs"));
        }
    }

    /**
     * 校验 plan.step.inputs 的结构：必须为 JSON 数组，且每个元素都是字段命中白名单的 JSON 对象。
     * <p>
     * inputs 为可选字段，为 null 时直接放行。
     *
     * @param value inputs 字段的原始值
     */
    private static void validateInputsShape(Object value) {
        // inputs 为可选字段：未提供时跳过校验
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONArray inputs)) {
            throw invalid("plan.step.inputs必须是数组");
        }
        // 逐输入校验：每个 input 必须是 JSON 对象且字段命中白名单
        for (Object inputValue : inputs) {
            if (!(inputValue instanceof JSONObject input)) {
                throw invalid("plan.step.inputs中的元素必须是JSON对象");
            }
            requireKnownFields(input, INPUT_FIELDS, "plan.step.input");
        }
    }

    /**
     * 校验 clarification 字段的结构：必须为 JSON 对象且字段命中澄清白名单。
     * <p>
     * clarification 为可选字段，为 null 时直接放行；该字段用于承载向用户提问
     * 澄清执行前置信息的交互数据。
     *
     * @param value clarification 字段的原始值
     */
    private static void validateClarificationShape(Object value) {
        // clarification 为可选字段：未提供时跳过校验
        if (value == null) {
            return;
        }
        if (!(value instanceof JSONObject clarification)) {
            throw invalid("clarification必须是JSON对象");
        }
        requireKnownFields(clarification, CLARIFICATION_FIELDS, "clarification");
    }

    /**
     * 字段白名单校验：检查对象的全部键是否都在允许集合内。
     * <p>
     * 一旦发现第一个未声明字段立即抛出协议错误，用于拦截模型输出的幻觉字段，
     * 防止未声明字段进入执行链路。
     *
     * @param object 待校验的 JSON 对象
     * @param fields 允许出现的字段集合（白名单）
     * @param path   当前对象在整体结构中的路径，用于错误信息定位
     */
    private static void requireKnownFields(JSONObject object, Set<String> fields, String path) {
        // 白名单校验核心：找出第一个不在允许集合内的键作为未声明字段拒绝
        String unknown = object.keySet().stream().filter(key -> !fields.contains(key)).findFirst().orElse(null);
        if (unknown != null) {
            throw invalid(path + "包含未声明字段：" + unknown);
        }
    }

    /**
     * 构造协议错误：统一以 409 Conflict 状态码表达解析阶段的协议/结构错误。
     * <p>
     * 使用 CONFLICT 而非参数错误，意在表达"Planner 输出与执行协议冲突"，
     * 与业务层的冲突语义保持一致。
     *
     * @param message 错误描述
     * @return 携带 409 状态码的业务异常
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
