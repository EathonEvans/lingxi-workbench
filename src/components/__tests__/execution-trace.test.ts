import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import ExecutionTrace from "../execution-trace";

describe("ExecutionTrace", () => {
  it("等待用户确认时不把中断中的操作显示为失败", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      streaming: false,
      waiting: true,
      subAgents: [{
        agentId: "10",
        agentName: "个人求职教练",
        delegationId: "delegation-1",
        sequence: 8,
        status: "FAILED",
      }],
    }));

    expect(html).toContain("等待你确认后继续");
    expect(html).not.toContain("所需操作失败");
  });

  it("消息终态失败时覆盖已完成操作摘要", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      errorMessage: "协作处理失败",
      failed: true,
      streaming: true,
      subAgents: [{
        agentId: "10",
        agentName: "岗位分析助手",
        delegationId: "delegation-1",
        sequence: 8,
        status: "SUCCEEDED",
      }],
      timeline: [{
        eventType: "RUN_COMPLETED",
        message: "处理完成",
        runId: "run-1",
        sequence: 9,
        steps: [],
      }],
    }));

    expect(html).toContain("协作处理失败");
    expect(html).not.toContain("处理完成");
  });

  it("在计划步骤内展示协作处理、阶段进度、所需操作和流式输出", () => {
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
        steps: [{
          capabilityCode: "career_coach",
          name: "分析目标岗位",
          status: "RUNNING",
          stepId: "analyze-role",
        }],
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
    expect(html).not.toContain("协作 Agent");
  });

  it("使用最新任务快照状态且不被协作过程的无状态事件覆盖", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      streaming: true,
      timeline: [{
        eventType: "PLAN_CREATED",
        planNo: 3,
        runId: "run-1",
        sequence: 10,
        steps: [{
          capabilityCode: "career_coach",
          name: "获取当前登录用户信息",
          status: "COMPLETED",
          stepId: "current-user",
        }],
      }, {
        eventType: "MODEL_COMPLETED",
        message: "本轮分析已完成",
        planNo: 3,
        runId: "run-1",
        sequence: 11,
        stepId: "current-user",
        steps: [],
      }, {
        eventType: "RUN_COMPLETED",
        message: "处理完成",
        runId: "run-1",
        sequence: 12,
        steps: [],
      }],
    }));

    expect(html).toContain("获取当前登录用户信息");
    expect(html).toContain("完成");
    expect(html).not.toContain("等待");
  });

  it("根运行失败时停止未完成计划的加载状态", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      errorMessage: "本轮可用额度已用完，请缩小问题范围后重试",
      failed: true,
      streaming: true,
      timeline: [{
        eventType: "PLAN_CREATED",
        planNo: 1,
        runId: "run-failed",
        sequence: 1,
        steps: [{
          capabilityCode: "career_coach",
          name: "总结高级Java工程师能力",
          status: "RUNNING",
          stepId: "career-summary",
        }, {
          capabilityCode: "",
          name: "给出准备建议",
          status: "PENDING",
          stepId: "final-advice",
        }],
      }, {
        eventType: "RUN_FAILED",
        message: "处理失败",
        runId: "run-failed",
        sequence: 2,
        steps: [],
      }],
    }));

    expect(html).toContain("总结高级Java工程师能力");
    expect(html).toContain("失败");
    expect(html).toContain("给出准备建议");
    expect(html).toContain("未执行");
    expect(html).not.toContain("执行中");
    expect(html).not.toContain('data-status="RUNNING"');
  });

  it("根运行取消时停止当前步骤并跳过未开始步骤", () => {
    const html = renderToStaticMarkup(createElement(ExecutionTrace, {
      streaming: true,
      timeline: [{
        eventType: "PLAN_CREATED",
        planNo: 1,
        runId: "run-cancelled",
        sequence: 1,
        steps: [{
          capabilityCode: "career_coach",
          name: "整理岗位要求",
          status: "RUNNING",
          stepId: "role-summary",
        }, {
          capabilityCode: "",
          name: "生成准备建议",
          status: "PENDING",
          stepId: "advice",
        }],
      }, {
        eventType: "RUN_CANCELLED",
        message: "处理已停止",
        runId: "run-cancelled",
        sequence: 2,
        steps: [],
      }],
    }));

    expect(html).toContain("已停止");
    expect(html).toContain("未执行");
    expect(html).not.toContain("执行中");
    expect(html).not.toContain('data-status="RUNNING"');
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
