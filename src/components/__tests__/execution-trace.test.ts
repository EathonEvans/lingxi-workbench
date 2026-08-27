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

  it("在计划步骤内展示子 Agent、阶段进度、工具和流式输出", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      streaming: true,
      stepOutputs: [{
        content: "岗位主要关注运行时边界与生产治理。",
        planNo: 2,
        runId: "run-1",
        sequence: 7,
        stepId: "analyze-role",
      }],
      subAgents: [{
        agentId: "30",
        agentName: "个人求职教练",
        delegationId: "delegation-1",
        planNo: 2,
        sequence: 3,
        status: "RUNNING",
        stepId: "analyze-role",
      }],
      timeline: [{
        eventType: "PLAN_CREATED",
        planNo: 2,
        runId: "run-1",
        sequence: 1,
        steps: [{ capabilityCode: "career_coach", name: "分析目标岗位", stepId: "analyze-role" }],
      }, {
        displayName: "分析目标岗位",
        eventType: "STEP_STARTED",
        planNo: 2,
        runId: "run-1",
        sequence: 2,
        stepId: "analyze-role",
        stepStatus: "RUNNING",
        steps: [],
      }, {
        eventType: "MODEL_STARTED",
        message: "正在分析岗位核心要求",
        planNo: 2,
        runId: "run-1",
        sequence: 4,
        stepId: "analyze-role",
        steps: [],
      }],
      tools: [{
        displayName: "知识搜索",
        durationMillis: 829,
        invocationId: "tool-1",
        planNo: 2,
        sequence: 6,
        status: "SUCCEEDED",
        stepId: "analyze-role",
        toolCode: "knowledge_search",
      }],
    }));

    expect(html).toContain("个人求职教练");
    expect(html).toContain("正在分析岗位核心要求");
    expect(html).toContain("知识搜索");
    expect(html).toContain("阶段输出");
    expect(html).toContain("岗位主要关注运行时边界与生产治理");
    expect(html).not.toContain(">工具调用<");
  });

  it("非计划执行只展示原有进度，不额外创建执行计划", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      progress: [{
        message: "正在评估已获得的信息",
        phase: "EVALUATING",
        roundNo: 1,
        sequence: 1,
        toolCallCount: 0,
      }],
      streaming: true,
    }));

    expect(html).toContain("正在评估已获得的信息");
    expect(html).not.toContain(">执行计划<");
  });
});
