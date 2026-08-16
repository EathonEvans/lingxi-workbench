package com.lingxi.workbench.infra.ai.tool;

/** HITL 门禁签发的单次 Tool 执行许可，同时接收实际执行终态。 */
public interface AgentToolApprovalPermit {

    void complete(String result);

    void fail(RuntimeException exception);

    static AgentToolApprovalPermit noop() {
        return NoopPermit.INSTANCE;
    }

    enum NoopPermit implements AgentToolApprovalPermit {
        INSTANCE;

        @Override
        public void complete(String result) {
        }

        @Override
        public void fail(RuntimeException exception) {
        }
    }
}
