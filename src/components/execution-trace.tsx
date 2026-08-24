"use client";

import {
  Bot,
  ChevronDown,
  Circle,
  CircleCheck,
  CircleX,
  LoaderCircle,
  Sparkles,
  Wrench,
} from "lucide-react";
import type {
  ChatAgentProgressEvent,
  ChatExecutionTimelineEvent,
  ChatStepOutputEvent,
  ChatSubAgentEvent,
  ChatToolEvent,
} from "@/lib/chat";
import styles from "./execution-trace.module.css";

type ExecutionTraceProps = {
  errorMessage?: string;
  failed?: boolean;
  progress?: ChatAgentProgressEvent[];
  streaming: boolean;
  subAgents?: ChatSubAgentEvent[];
  stepOutputs?: ChatStepOutputEvent[];
  timeline?: ChatExecutionTimelineEvent[];
  tools?: ChatToolEvent[];
};

type TraceStatus = "completed" | "failed" | "reused" | "running";

type ToolGroup = {
  displayName: string;
  durationMillis: number;
  events: ChatToolEvent[];
  status: TraceStatus;
  toolCode: string;
};

function formatDuration(durationMillis: number): string {
  return durationMillis >= 1000
    ? `${(durationMillis / 1000).toFixed(1)}s`
    : `${durationMillis}ms`;
}

function statusIcon(status: TraceStatus) {
  if (status === "running") return <LoaderCircle size={14} />;
  if (status === "failed") return <CircleX size={14} />;
  if (status === "reused") return <Sparkles size={14} />;
  return <CircleCheck size={14} />;
}

function groupTools(tools: ChatToolEvent[]): ToolGroup[] {
  const groups = new Map<string, ChatToolEvent[]>();
  tools.forEach((tool) => {
    groups.set(tool.toolCode, [...(groups.get(tool.toolCode) ?? []), tool]);
  });
  return [...groups.entries()].map(([toolCode, events]) => {
    let status: TraceStatus = "completed";
    if (events.some((event) => event.status === "RUNNING")) status = "running";
    else if (events.some((event) => event.status === "FAILED")) status = "failed";
    else if (events.some((event) => event.status === "REUSED")) status = "reused";
    return {
      displayName: events.at(-1)?.displayName || toolCode,
      durationMillis: events.reduce((total, event) => total + (event.durationMillis ?? 0), 0),
      events,
      status,
      toolCode,
    };
  });
}

function traceSummary(
  failed: boolean,
  errorMessage: string | undefined,
  streaming: boolean,
  toolGroups: ToolGroup[],
  subAgents: ChatSubAgentEvent[],
  timeline: ChatExecutionTimelineEvent[],
  progress: ChatAgentProgressEvent[],
): { label: string; status: TraceStatus } {
  const latestTimeline = timeline.at(-1);
  const terminal = timeline.findLast((event) =>
    ["RUN_CANCELLED", "RUN_COMPLETED", "RUN_FAILED"].includes(event.eventType),
  );
  if (failed) {
    return {
      label: errorMessage || terminal?.message || "执行失败",
      status: "failed",
    };
  }
  const runningTool = toolGroups.find((group) => group.status === "running");
  const runningAgent = subAgents.find((agent) => agent.status === "RUNNING");
  if (runningTool) return { label: `正在执行：${runningTool.displayName}`, status: "running" };
  if (runningAgent) return { label: `正在协作：${runningAgent.agentName}`, status: "running" };
  if (latestTimeline?.stepStatus === "RUNNING") {
    return { label: `正在处理：${latestTimeline.displayName || "当前步骤"}`, status: "running" };
  }
  if (latestTimeline?.eventType === "PLANNING") return { label: "正在规划任务", status: "running" };
  if (latestTimeline?.eventType === "REPLANNING") {
    return {
      label: latestTimeline.message || "正在调整执行计划",
      status: "running",
    };
  }
  if (latestTimeline?.eventType === "FINALIZING") {
    return {
      label: latestTimeline.message || "正在基于现有信息整理回答",
      status: "reused",
    };
  }
  const operationCount = toolGroups.reduce((total, group) => total + group.events.length, 0)
    + subAgents.length;
  if (terminal?.eventType === "RUN_COMPLETED" && !streaming) {
    return {
      label: terminal.message
        || (operationCount > 0 ? `处理完成 · ${operationCount} 项操作` : "处理完成"),
      status: "completed",
    };
  }
  const hasFailure = toolGroups.some((group) => group.status === "failed")
    || subAgents.some((agent) => agent.status === "FAILED")
    || terminal?.eventType === "RUN_FAILED";
  if (hasFailure) {
    return {
      label: terminal?.message || latestTimeline?.message || "执行过程存在异常",
      status: "failed",
    };
  }
  const reused = toolGroups.some((group) => group.status === "reused");
  if (streaming && reused) return { label: "已复用已有结果，正在整理回答", status: "reused" };
  if (streaming && latestTimeline?.eventType === "STEP_COMPLETED") {
    return { label: "步骤完成，正在整理回答", status: "running" };
  }
  const latestProgress = progress.at(-1);
  if (streaming && latestProgress) {
    return { label: latestProgress.message, status: "running" };
  }
  if (streaming && operationCount > 0) {
    return { label: `已完成 ${operationCount} 次操作，正在评估结果`, status: "running" };
  }
  if (streaming) return { label: "正在理解问题", status: "running" };
  return {
    label: operationCount > 0 ? `处理完成 · ${operationCount} 项操作` : "处理完成",
    status: reused ? "reused" : "completed",
  };
}

export default function ExecutionTrace({
  errorMessage,
  failed = false,
  progress = [],
  streaming,
  subAgents = [],
  stepOutputs = [],
  timeline = [],
  tools = [],
}: ExecutionTraceProps) {
  const toolGroups = groupTools(tools);
  const summary = traceSummary(
    failed,
    errorMessage,
    streaming,
    toolGroups,
    subAgents,
    timeline,
    progress,
  );
  const visibleProgress = progress.reduce<ChatAgentProgressEvent[]>((events, event) => {
    const previous = events.at(-1);
    if (previous?.phase === event.phase && previous.message === event.message) {
      return [...events.slice(0, -1), event];
    }
    return [...events, event];
  }, []).slice(-4);
  const plan = timeline.findLast((event) => event.steps.length > 0);
  const statusByStep = new Map(
    timeline
      .filter((event) => event.planNo === plan?.planNo && event.stepId)
      .map((event) => [event.stepId, event.stepStatus] as const),
  );
  const outputByStep = new Map<string, string>();
  stepOutputs
    .filter((event) => event.planNo === plan?.planNo)
    .forEach((event) => {
      outputByStep.set(event.stepId, `${outputByStep.get(event.stepId) ?? ""}${event.content}`);
    });
  const notices = timeline
    .filter(
      (event) =>
        event.message
        && ["FINALIZING", "REPLANNING", "RUN_FAILED"].includes(event.eventType),
    )
    .slice(-3);
  const hasDetails = toolGroups.length > 0
    || visibleProgress.length > 0
    || subAgents.length > 0
    || notices.length > 0
    || Boolean(plan?.steps.length);

  if (!hasDetails) {
    return (
      <div className={`${styles.summary} ${styles[summary.status]}`} role="status">
        {statusIcon(summary.status)}
        <span>{summary.label}</span>
      </div>
    );
  }

  return (
    <details
      className={styles.trace}
      data-testid="execution-trace"
      open={streaming || undefined}
    >
      <summary className={`${styles.summary} ${styles[summary.status]}`}>
        {statusIcon(summary.status)}
        <span>{summary.label}</span>
        <ChevronDown className={styles.chevron} size={14} />
      </summary>
      <div className={styles.details}>
        {visibleProgress.length ? (
          <section className={styles.section} aria-label="执行进度">
            <span className={styles.sectionLabel}>执行进度</span>
            <ol className={styles.progressList}>
              {visibleProgress.map((event, index) => {
                const active = streaming && index === visibleProgress.length - 1;
                return (
                  <li key={event.sequence} data-active={active || undefined}>
                    <span className={styles.progressIcon}>
                      {active ? <LoaderCircle size={12} /> : <CircleCheck size={12} />}
                    </span>
                    <span>{event.message}</span>
                  </li>
                );
              })}
            </ol>
          </section>
        ) : null}

        {notices.length ? (
          <section className={styles.notices} aria-label="执行提示">
            {notices.map((notice) => (
              <div
                className={notice.eventType === "RUN_FAILED" ? styles.noticeError : styles.noticeInfo}
                key={notice.sequence}
              >
                {notice.eventType === "RUN_FAILED" ? <CircleX size={12} /> : <Sparkles size={12} />}
                <span>{notice.message}</span>
              </div>
            ))}
          </section>
        ) : null}

        {plan?.steps.length ? (
          <section className={styles.section}>
            <span className={styles.sectionLabel}>执行计划</span>
            <ol className={styles.plan}>
              {plan.steps.map((step) => {
                const status = statusByStep.get(step.stepId);
                const output = outputByStep.get(step.stepId)?.trim();
                return (
                  <li className={styles.planItem} key={`${plan.planNo}:${step.stepId}`}>
                    <div className={styles.planRow}>
                      <span className={styles.planIcon} data-status={status ?? "PENDING"}>
                        {status === "COMPLETED" ? <CircleCheck size={13} /> : status === "FAILED" ? <CircleX size={13} /> : status === "RUNNING" ? <LoaderCircle size={13} /> : <Circle size={13} />}
                      </span>
                      <span>{step.name}</span>
                      <small>{status === "RUNNING" ? "执行中" : status === "COMPLETED" ? "完成" : status === "FAILED" ? "失败" : "等待"}</small>
                    </div>
                    {output ? (
                      <p className={styles.stepOutput} data-live={status === "RUNNING" || undefined}>
                        {output}
                      </p>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          </section>
        ) : null}

        {toolGroups.length ? (
          <section className={styles.section}>
            <span className={styles.sectionLabel}>工具调用</span>
            <ul className={styles.operations}>
              {toolGroups.map((group) => (
                <li key={group.toolCode}>
                  <span className={`${styles.operationIcon} ${styles[group.status]}`}>
                    {group.status === "failed" ? <Wrench size={13} /> : statusIcon(group.status)}
                  </span>
                  <span className={styles.operationName}>{group.displayName}</span>
                  {group.events.length > 1 ? <span className={styles.count}>× {group.events.length}</span> : null}
                  <span className={styles.operationMeta}>
                    {group.status === "reused" ? "已复用" : group.status === "running" ? "执行中" : group.status === "failed" ? "失败" : "完成"}
                    {group.durationMillis > 0 ? ` · ${formatDuration(group.durationMillis)}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        {subAgents.length ? (
          <section className={styles.section}>
            <span className={styles.sectionLabel}>协作 Agent</span>
            <ul className={styles.operations}>
              {subAgents.map((agent) => (
                <li key={agent.delegationId}>
                  <span className={`${styles.operationIcon} ${styles[agent.status === "RUNNING" ? "running" : agent.status === "FAILED" ? "failed" : "completed"]}`}>
                    <Bot size={13} />
                  </span>
                  <span className={styles.operationName}>{agent.agentName}</span>
                  <span className={styles.operationMeta}>
                    {agent.status === "RUNNING" ? "协作中" : agent.status === "FAILED" ? "未完成" : "已完成"}
                    {agent.durationMillis ? ` · ${formatDuration(agent.durationMillis)}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </div>
    </details>
  );
}
