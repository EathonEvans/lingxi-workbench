package com.lingxi.workbench.agentexecute.loop;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单次 Agent 执行的工具结果账本。
 *
 * <p>账本只保存确定性只读工具的成功结果，生命周期与一次模型回答一致。
 * 工具参数只参与内存中的 SHA-256 指纹计算，不进入事件、日志或持久化。</p>
 */
public final class AgentToolExecutionLedger {

    private final Map<String, String> completedResults = new HashMap<>();

    /**
     * 查询同一工具与规范化参数是否已有成功结果。
     */
    public synchronized Optional<String> findReusableResult(AgentToolDefinition definition, String toolInput) {
        if (!definition.getExecutionMode().canReuseResult()) {
            return Optional.empty();
        }
        return Optional.ofNullable(completedResults.get(fingerprint(definition.getCode(), toolInput)));
    }

    /**
     * 记录一次可安全复用的成功结果。
     */
    public synchronized void recordSuccessfulResult(AgentToolDefinition definition, String toolInput, String result) {
        if (definition.getExecutionMode().canReuseResult()) {
            completedResults.put(fingerprint(definition.getCode(), toolInput), result);
        }
    }

    /**
     * 将已有结果包装为模型可识别的复用响应，并要求停止重复调用。
     */
    public String reuseResponse() {
        JSONObject response = new JSONObject();
        response.put("status", "REUSED");
        response.put("instruction", "相同工具和参数已经执行，结果不会变化；请使用对话中已有的工具结果并直接回答，不要继续调用工具");
        return response.toJSONString();
    }

    private String fingerprint(String toolCode, String toolInput) {
        try {
            Object parsed = JSON.parse(toolInput);
            byte[] canonicalInput = JSON.toJSONBytes(sort(parsed));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(toolCode.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(canonicalInput));
        } catch (RuntimeException | NoSuchAlgorithmException exception) {
            throw new AgentToolException("工具参数无法生成执行指纹", exception);
        }
    }

    /**
     * 递归排序 JSON 结构：对象按字段名升序，数组保持原顺序，保证相同语义输入产生相同指纹。
     */
    private static Object sort(Object node) {
        if (node instanceof JSONObject object) {
            JSONObject sorted = new JSONObject();
            List<String> fieldNames = new ArrayList<>(object.keySet());
            fieldNames.sort(Comparator.naturalOrder());
            for (String fieldName : fieldNames) {
                sorted.put(fieldName, sort(object.get(fieldName)));
            }
            return sorted;
        }
        if (node instanceof JSONArray array) {
            JSONArray sorted = new JSONArray();
            for (Object child : array) {
                sorted.add(sort(child));
            }
            return sorted;
        }
        return node;
    }
}
