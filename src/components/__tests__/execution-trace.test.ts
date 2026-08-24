import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import ExecutionTrace from "../execution-trace";

describe("ExecutionTrace", () => {
  it("消息终态失败时覆盖已完成操作摘要", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      errorMessage: "下游 Agent 执行失败",
      failed: true,
      streaming: false,
      subAgents: [{
        agentId: "10",
        agentName: "岗位分析助手",
        delegationId: "delegation-1",
        sequence: 8,
        status: "SUCCEEDED",
      }],
      timeline: [{
        eventType: "RUN_COMPLETED",
        message: "Agent运行已完成",
        runId: "run-1",
        sequence: 9,
        steps: [],
      }],
    }));

    expect(html).toContain("下游 Agent 执行失败");
    expect(html).not.toContain("处理完成");
  });
});
