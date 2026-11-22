package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONArray;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolReconciliation;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationResult;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/** MCP Tool 的外部效果查询适配器，固定使用控制面声明的只读查询 Tool。 */
public final class McpToolReconciliation implements AgentToolReconciliation {

    private final RuntimeToolEntity tool;
    private final McpRuntimeToolExecutor callback;

    public McpToolReconciliation(RuntimeToolEntity tool, McpRuntimeToolExecutor callback) {
        this.tool = tool;
        this.callback = callback;
    }

    @Override
    public AgentToolReconciliationResult query(AgentToolReconciliationRequest request) {
        JSONObject arguments = new JSONObject();
        arguments.put("idempotencyKey", request.idempotencyKey());
        arguments.put("toolCode", tool.getCode());
        arguments.put("toolInput", JSON.parseObject(request.toolInput()));
        String response = callback.callRemoteTool(tool.getReconciliationQueryToolName(),
                new AgentToolRequest(JSON.toJSONString(arguments), request.executionContext(), request.idempotencyKey()));
        return parseResponse(response);
    }

    /**
     * 远端查询 Tool 的文本内容必须是 JSON：status 为 SUCCEEDED、NOT_APPLIED、PENDING 或
     * COMPENSATION_REQUIRED；成功结果可放在 result，错误只允许返回稳定 errorCode。
     */
    static AgentToolReconciliationResult parseResponse(String response) {
        JSONArray contents = JSON.parseArray(response);
        if (contents == null) {
            throw new AgentToolException("MCP对账查询返回内容不合法");
        }
        for (int index = 0; index < contents.size(); index++) {
            JSONObject content = contents.getJSONObject(index);
            if (content == null || !"text".equals(content.getString("type"))) {
                continue;
            }
            JSONObject payload = JSON.parseObject(content.getString("text"));
            if (payload == null) {
                throw new AgentToolException("MCP对账查询文本必须是JSON对象");
            }
            try {
                AgentToolReconciliationStatus status = AgentToolReconciliationStatus.valueOf(payload.getString("status"));
                return new AgentToolReconciliationResult(status, payload.getString("result"), payload.getString("errorCode"));
            } catch (IllegalArgumentException exception) {
                throw new AgentToolException("MCP对账查询状态不受支持", exception);
            }
        }
        throw new AgentToolException("MCP对账查询未返回文本结果");
    }

}
