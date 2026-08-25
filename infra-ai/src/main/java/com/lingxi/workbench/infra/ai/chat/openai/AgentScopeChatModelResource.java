package com.lingxi.workbench.infra.ai.chat.openai;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.OkHttpTransport;

import java.util.Objects;

/** AgentScope 模型和本次调用专属 HTTP 资源。 */
public final class AgentScopeChatModelResource implements AutoCloseable {

    private final Model model;
    private final OkHttpTransport transport;

    public AgentScopeChatModelResource(Model model, OkHttpTransport transport) {
        this.model = Objects.requireNonNull(model, "AgentScope模型不能为空");
        this.transport = Objects.requireNonNull(transport, "AgentScope HTTP传输不能为空");
    }

    /** 返回仅供本次 Agent 调用使用的模型。 */
    public Model model() {
        return model;
    }

    /** 关闭传输连接池，避免凭证轮换后保留旧客户端。 */
    @Override
    public void close() {
        transport.close();
    }

    OkHttpTransport transport() {
        return transport;
    }
}
