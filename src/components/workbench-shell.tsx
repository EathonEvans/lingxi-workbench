"use client";

import { ActionIcon, Avatar, Button, Markdown, Tag } from "@lobehub/ui";
import { ChatInputArea } from "@lobehub/ui/chat";
import { Drawer, Dropdown, Empty, Input, Spin } from "antd";
import {
  Archive,
  ArrowUp,
  Bot,
  Boxes,
  ChevronDown,
  ChevronsUpDown,
  Circle,
  CircleCheck,
  CircleX,
  FileSearch,
  FolderKanban,
  History,
  ListTodo,
  LoaderCircle,
  LogOut,
  Menu,
  MessageSquareText,
  Moon,
  MoreHorizontal,
  Paperclip,
  Plus,
  RotateCcw,
  Search,
  Settings2,
  Sparkles,
  Square,
  Sun,
  Trash2,
  WandSparkles,
  Wrench,
} from "lucide-react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  type AvailableApplication,
  type ChatExecutionTimelineEvent,
  type ChatSubAgentEvent,
  type ChatToolEvent,
  type Conversation,
  ChatStreamError,
  listAvailableApplications,
  listConversationMessages,
} from "@/lib/chat";
import { useAuth } from "./auth-provider";
import { useThemeMode } from "./app-provider";
import { type ChatMessage, useChatStream } from "@/hooks/use-chat-stream";
import { useConversation } from "@/hooks/use-conversation";
import styles from "./workbench-shell.module.css";

// ─── 建议卡片 ─────────────────────────────────────────────────

const suggestions = [
  {
    description: "从知识库中提炼重点，并给出结论依据",
    icon: FileSearch,
    title: "总结企业知识",
  },
  {
    description: "把目标拆成可执行任务与关键里程碑",
    icon: FolderKanban,
    title: "规划复杂项目",
  },
  {
    description: "基于数据发现趋势、异常与改进机会",
    icon: Sparkles,
    title: "洞察业务数据",
  },
  {
    description: "快速起草、润色和重构专业内容",
    icon: WandSparkles,
    title: "协作内容创作",
  },
];

// ─── 子组件 ───────────────────────────────────────────────────

function SubAgentExecutionList({
  subAgents,
}: {
  subAgents: ChatSubAgentEvent[];
}) {
  return (
    <div className={styles.subAgentList}>
      {subAgents.map((agent) => (
        <div
          className={`${styles.executionItem} ${
            agent.status === "RUNNING"
              ? styles.executionRunning
              : agent.status === "FAILED"
                ? styles.executionFailed
                : styles.executionDone
          }`}
          key={agent.delegationId}
        >
          {agent.status === "RUNNING" ? (
            <LoaderCircle className={styles.executionSpinner} size={13} />
          ) : agent.status === "FAILED" ? (
            <Wrench size={13} />
          ) : null}
          <span className={styles.executionLabel}>委派：{agent.agentName}</span>
          {agent.durationMillis ? (
            <span className={styles.executionDuration}>
              {agent.durationMillis >= 1000
                ? `${(agent.durationMillis / 1000).toFixed(1)}s`
                : `${agent.durationMillis}ms`}
            </span>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function ToolExecutionList({ tools }: { tools: ChatToolEvent[] }) {
  return (
    <div className={styles.toolList}>
      {tools.map((tool) => (
        <div
          className={`${styles.executionItem} ${
            tool.status === "RUNNING"
              ? styles.executionRunning
              : tool.status === "FAILED"
                ? styles.executionFailed
                : styles.executionDone
          }`}
          key={tool.invocationId}
        >
          {tool.status === "RUNNING" ? (
            <LoaderCircle className={styles.executionSpinner} size={13} />
          ) : tool.status === "FAILED" ? (
            <Wrench size={13} />
          ) : null}
          <span className={styles.executionLabel}>{tool.displayName}</span>
          {tool.durationMillis ? (
            <span className={styles.executionDuration}>
              {tool.durationMillis >= 1000
                ? `${(tool.durationMillis / 1000).toFixed(1)}s`
                : `${tool.durationMillis}ms`}
            </span>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function ExecutionTimeline({ events }: { events: ChatExecutionTimelineEvent[] }) {
  const latest = events.at(-1);
  const plan = events.findLast((event) => event.steps.length > 0);
  const stepEvents = events.filter((event) => event.stepId);
  const steps = plan?.steps ?? [];
  const statusByStep = new Map(
    stepEvents.map((event) => [event.stepId, event.stepStatus] as const),
  );
  const completedSteps = [...statusByStep.values()].filter(
    (status) => status === "COMPLETED",
  ).length;

  const summary = (() => {
    if (!latest) return "正在准备";
    if (latest.eventType === "PLANNING") return "正在规划任务";
    if (latest.eventType === "REPLANNING") return "正在调整执行计划";
    if (latest.eventType === "RUN_COMPLETED") return `已完成 ${completedSteps} 个步骤`;
    if (latest.eventType === "RUN_FAILED") return "执行未完成";
    if (latest.eventType === "RUN_CANCELLED") return "执行已停止";
    if (latest.stepStatus === "RUNNING") return `正在处理：${latest.displayName || "当前步骤"}`;
    if (steps.length > 0) return `执行计划 · ${completedSteps}/${steps.length}`;
    return latest.message || "正在执行任务";
  })();

  const summaryStatus = latest?.eventType === "RUN_FAILED" || latest?.stepStatus === "FAILED"
    ? "failed"
    : latest?.eventType === "RUN_COMPLETED"
      ? "completed"
      : "running";

  if (steps.length === 0) {
    return (
      <div className={`${styles.timelineSummary} ${styles[`timeline${summaryStatus}`]}`} role="status">
        {summaryStatus === "running" ? <LoaderCircle size={14} /> : summaryStatus === "failed" ? <CircleX size={14} /> : <CircleCheck size={14} />}
        <span>{summary}</span>
      </div>
    );
  }

  return (
    <details className={styles.timeline} data-testid="execution-timeline">
      <summary className={`${styles.timelineSummary} ${styles[`timeline${summaryStatus}`]}`}>
        {summaryStatus === "running" ? <LoaderCircle size={14} /> : summaryStatus === "failed" ? <CircleX size={14} /> : <CircleCheck size={14} />}
        <span>{summary}</span>
        <ChevronDown className={styles.timelineChevron} size={14} />
      </summary>
      <ol className={styles.timelineSteps} aria-label="执行步骤">
        {steps.map((step) => {
          const status = statusByStep.get(step.stepId);
          return (
            <li className={`${styles.timelineStep} ${styles[`timelineStep${status ?? "PENDING"}`]}`} key={step.stepId}>
              <span className={styles.timelineStepIcon} aria-hidden="true">
                {status === "COMPLETED" ? <CircleCheck size={14} /> : status === "FAILED" ? <CircleX size={14} /> : status === "RUNNING" ? <LoaderCircle size={14} /> : <Circle size={14} />}
              </span>
              <span>{step.name}</span>
            </li>
          );
        })}
      </ol>
    </details>
  );
}

// ─── 工具函数 ─────────────────────────────────────────────────

function formatConversationTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffDays === 0) {
    return date.toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  if (diffDays === 1) return "昨天";
  if (diffDays < 7) return `${diffDays}天前`;
  return date.toLocaleDateString("zh-CN", {
    month: "short",
    day: "numeric",
  });
}

// ─── 主组件 ───────────────────────────────────────────────────

function toChatMessage(msg: {
  content: string;
  id: string;
  role: string;
  status: string;
  errorMessage?: string;
}): ChatMessage | undefined {
  const role = msg.role === "USER" ? "user" : "assistant";
  if (msg.role === "SYSTEM") return undefined;
  let status: ChatMessage["status"] = "done";
  if (msg.status === "GENERATING") status = "streaming";
  else if (msg.status === "FAILED" || msg.status === "INTERRUPTED")
    status = "error";
  else if (msg.status === "CANCELLED") status = "stopped";
  return { content: msg.content, errorMessage: msg.errorMessage, id: msg.id, role, status };
}

export default function WorkbenchShell() {
  const router = useRouter();
  const { logout, ready, session } = useAuth();
  const { appearance, toggleAppearance } = useThemeMode();

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [selectedApplicationCode, setSelectedApplicationCode] =
    useState<string>("");
  const [applications, setApplications] = useState<AvailableApplication[]>([]);
  const [applicationLoading, setApplicationLoading] = useState(true);
  const [input, setInput] = useState("");
  const [requestError, setRequestError] = useState<string>();

  const messageEndRef = useRef<HTMLDivElement>(null);

  // ─── 通用错误处理 ─────────────────────────────────────────

  const handleRequestError = useCallback((error: unknown, fallback: string) => {
    if (error instanceof ChatStreamError && error.status === 401) {
      logout();
      router.replace("/login");
      return;
    }
    setRequestError(error instanceof ChatStreamError ? error.message : fallback);
  }, [logout, router]);

  // ─── 会话管理 Hook ────────────────────────────────────────

  const {
    conversationId: currentConversationId,
    conversationStatus,
    recentConversations,
    conversationLoading,
    historyOpen,
    historyConversations,
    historyLoading,
    historyCurrent,
    historyTotal,
    historyKeyword,
    messageLoading,
    messageCurrent,
    messageTotal,
    renamingId,
    renamingTitle,
    setConversationId,
    setConversationStatus,
    setHistoryOpen,
    setHistoryKeyword,
    refreshRecentConversations,
    loadHistoryConversations,
    loadOlderMessages,
    beginRename,
    cancelRename,
    submitRename,
    setRenamingTitle,
    archiveSelectedConversation,
    deleteSelectedConversation,
  } = useConversation({ session, onRequestError: handleRequestError });

  // ─── 对话流 Hook ───────────────────────────────────────────

  const handleLogout = useCallback(() => {
    logout();
    router.replace("/login");
  }, [logout, router]);

  const handleConversationCreated = useCallback((id: string) => {
    setConversationId(id);
    setConversationStatus("ACTIVE");
  }, [setConversationId, setConversationStatus]);

  const {
    messages,
    setMessages,
    streaming,
    streamError,
    pendingClarification,
    resumeAvailable,
    sendPrompt,
    stopStreaming,
    retryLastPrompt,
    answerClarification,
    clearError,
  } = useChatStream({
    session,
    selectedApplicationCode,
    conversationId: currentConversationId,
    onConversationCreated: handleConversationCreated,
    onLogout: handleLogout,
  });

  useEffect(() => {
    if (ready && !session) {
      router.replace("/login");
    }
  }, [ready, router, session]);

  // ─── 应用选择 ──────────────────────────────────────────────

  const selectedApplication = applications.find(
    (app) => app.code === selectedApplicationCode,
  );

  useEffect(() => {
    if (!session) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setApplicationLoading(true);

    listAvailableApplications(session)
      .then((apps) => {
        setApplications(apps);
        if (apps.length > 0 && !selectedApplicationCode) {
          setSelectedApplicationCode(apps[0].code);
        }
      })
      .catch((error) => handleRequestError(error, "获取可用应用失败"))
      .finally(() => setApplicationLoading(false));
    }, [handleRequestError, session, selectedApplicationCode]);

  useEffect(() => {
    if (!session) return;
    void refreshRecentConversations();
  }, [session, refreshRecentConversations]);

  // ─── 会话选择（加载历史消息） ──────────────────────────────

  const selectConversation = async (conversation: Conversation) => {
    if ((streaming || pendingClarification) && conversation.id !== currentConversationId) {
      clearError();
      return;
    }
    setConversationId(conversation.id);
    setConversationStatus(conversation.status);
    setHistoryOpen(false);
    clearError();

    const s = session;
    if (!s) return;

    setConversationId(conversation.id);
    setConversationStatus(conversation.status);

    try {
      const result = await listConversationMessages(conversation.id, s, {
        current: 1,
        size: 50,
      });
      const historyMessages = result.records
        .toReversed()
        .map(toChatMessage)
        .filter((m): m is ChatMessage => m !== undefined);
      setMessages(historyMessages);
    } catch (error) {
      handleRequestError(error, "加载历史消息失败");
    }
  };

  // ─── 加载更早消息 ─────────────────────────────────────────

  const handleLoadOlder = async () => {
    if (!currentConversationId || !session) return;
    await loadOlderMessages();
    // Reload after older message pagination advances
    if (currentConversationId && session) {
      try {
        const result = await listConversationMessages(
          currentConversationId,
          session,
          { current: messageCurrent + 1, size: 50 },
        );
        const olderMessages = result.records
          .toReversed()
          .map(toChatMessage)
          .filter((m): m is ChatMessage => m !== undefined);
        setMessages((current) => [...olderMessages, ...current]);
      } catch {
        // silently fail
      }
    }
  };

  // ─── 快捷操作 ──────────────────────────────────────────────

  const startTask = (prefix: string) => {
    setInput(prefix);
  };

  const startNewChat = () => {
    if (streaming) return;
    setConversationId(null);
    setConversationStatus(undefined);
    setMessages([]);
    clearError();
    setInput("");
  };

  // ─── 自动滚动 ──────────────────────────────────────────────

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // ─── 加载状态 ──────────────────────────────────────────────

  if (!ready || !session) {
    return (
      <main className={styles.authLoading} data-theme={appearance}>
        <span className={styles.authLoadingMark}>
          <Sparkles size={21} />
        </span>
        <p>正在进入灵犀工作台…</p>
      </main>
    );
  }

  // ─── 应用选择菜单 ─────────────────────────────────────────

  const applicationMenuItems = applications.map((app) => ({
    key: app.code,
    label: (
      <div className={styles.appMenuItem}>
        <strong>{app.name}</strong>
        {app.description && <small>{app.description}</small>}
      </div>
    ),
  }));

  // ─── 渲染会话项 ───────────────────────────────────────────

  const renderConversation = (
    conversation: Conversation,
    showStatus = false,
  ) => {
    const selected = conversation.id === currentConversationId;
    const menuItems = [
      { key: "rename", label: "重命名" },
      ...(conversation.status === "ACTIVE"
        ? [{ icon: <Archive size={14} />, key: "archive", label: "归档" }]
        : []),
      { danger: true, icon: <Trash2 size={14} />, key: "delete", label: "删除" },
    ];
    return (
      <div
        className={`${styles.conversation} ${selected ? styles.conversationActive : ""}`}
        key={conversation.id}
      >
        {renamingId === conversation.id ? (
          <div className={styles.conversationSelect}>
            <MessageSquareText size={16} />
            <span>
              <input
                autoFocus
                className={styles.conversationRename}
                maxLength={100}
                onBlur={() => void submitRename(conversation)}
                onChange={(event) => setRenamingTitle(event.target.value)}
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => {
                  if (event.key === "Enter") event.currentTarget.blur();
                  if (event.key === "Escape") {
                    event.preventDefault();
                    cancelRename();
                  }
                }}
                value={renamingTitle}
              />
              <small>
                {formatConversationTime(
                  conversation.lastMessageAt || conversation.updatedAt,
                )}
              </small>
            </span>
          </div>
        ) : (
          <button
            className={styles.conversationSelect}
            onClick={() => void selectConversation(conversation)}
            type="button"
          >
            <MessageSquareText size={16} />
            <span>
              <strong>{conversation.title}</strong>
              <small>
                {showStatus && conversation.status === "ARCHIVED"
                  ? "已归档 · "
                  : ""}
                {formatConversationTime(
                  conversation.lastMessageAt || conversation.updatedAt,
                )}
              </small>
            </span>
          </button>
        )}
        <Dropdown
          menu={{
            items: menuItems,
            onClick: ({ domEvent, key }) => {
              domEvent.stopPropagation();
              if (key === "rename") beginRename(conversation);
              if (key === "archive")
                void archiveSelectedConversation(conversation);
              if (key === "delete")
                void deleteSelectedConversation(conversation);
            },
          }}
          placement="bottomRight"
          trigger={["click"]}
        >
          <button
            aria-label={`管理会话：${conversation.title}`}
            className={styles.conversationMore}
            onClick={(event) => event.stopPropagation()}
            type="button"
          >
            <MoreHorizontal size={15} />
          </button>
        </Dropdown>
      </div>
    );
  };

  // ─── JSX ────────────────────────────────────────────────────

  return (
    <main className={styles.shell} data-theme={appearance}>
      {/* 侧边栏 */}
      <aside
        className={`${styles.sidebar} ${sidebarOpen ? styles.sidebarOpen : ""}`}
      >
        <div className={styles.brandRow}>
          <div className={styles.brandMark}>
            <Image
              alt=""
              className={styles.brandLogo}
              height={32}
              priority
              src="/lingxi-logo.png"
              style={{ height: "auto" }}
              width={36}
            />
          </div>
          <div className={styles.brandText}>
            <strong>灵犀</strong>
            <span>AI Workbench</span>
          </div>
          <ActionIcon
            className={styles.mobileClose}
            icon={Menu}
            onClick={() => setSidebarOpen(false)}
            title="收起导航"
          />
        </div>

        <Button
          block
          className={styles.newChat}
          icon={Plus}
          onClick={startNewChat}
          type="primary"
        >
          新建对话
        </Button>

        <div className={styles.historyHeader}>
          <span>置顶对话</span>
        </div>
        <div className={styles.pinnedConversationList}>
          <span className={styles.conversationEmpty}>暂无置顶对话</span>
        </div>

        <div
          className={`${styles.historyHeader} ${styles.recentHistoryHeader}`}
        >
          <span>最近对话</span>
          <ActionIcon
            icon={Search}
            onClick={() => setHistoryOpen(true)}
            size="small"
            title="搜索对话"
          />
        </div>
        <div className={styles.conversationList}>
          {conversationLoading ? (
            <div className={styles.conversationState}>
              <Spin size="small" />
            </div>
          ) : recentConversations.length > 0 ? (
            recentConversations.map((conv) => renderConversation(conv))
          ) : (
            <span className={styles.conversationEmpty}>暂无会话记录</span>
          )}
        </div>

        <div className={styles.sidebarFooter}>
          <button
            className={styles.navItem}
            onClick={() => setHistoryOpen(true)}
            type="button"
          >
            <History size={17} />
            全部历史
          </button>
          <button className={styles.navItem} type="button">
            <Settings2 size={17} />
            设置
          </button>
          <div className={styles.profile}>
            <Avatar
              avatar={
                session.user.avatarUrl ||
                session.user.displayName.slice(0, 1)
              }
              background={appearance === "dark" ? "#25324a" : "#e8f1ff"}
              size={34}
            />
            <span>
              <strong>{session.user.displayName}</strong>
              <small>个人空间</small>
            </span>
            <ActionIcon
              icon={LogOut}
              onClick={handleLogout}
              size="small"
              title="退出登录"
            />
          </div>
        </div>
      </aside>

      {sidebarOpen && (
        <button
          aria-label="关闭导航"
          className={styles.backdrop}
          onClick={() => setSidebarOpen(false)}
          type="button"
        />
      )}

      {/* 历史抽屉 */}
      <Drawer
        className={styles.historyDrawer}
        destroyOnHidden
        onClose={() => setHistoryOpen(false)}
        open={historyOpen}
        placement="left"
        title="全部会话"
        size={380}
      >
        <Input.Search
          allowClear
          onChange={(event) => setHistoryKeyword(event.target.value)}
          onSearch={(keyword) => void loadHistoryConversations(1, keyword)}
          placeholder="搜索会话标题"
          value={historyKeyword}
        />
        <div className={styles.historyList}>
          {historyLoading && historyConversations.length === 0 ? (
            <div className={styles.historyLoading}>
              <Spin />
            </div>
          ) : historyConversations.length > 0 ? (
            historyConversations.map((conv) => renderConversation(conv, true))
          ) : (
            <Empty
              description="暂无会话记录"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          )}
        </div>
        {historyConversations.length < historyTotal && (
          <Button
            block
            loading={historyLoading}
            onClick={() =>
              void loadHistoryConversations(
                historyCurrent + 1,
                historyKeyword,
                true,
              )
            }
          >
            加载更多
          </Button>
        )}
      </Drawer>

      {/* 工作区 */}
      <section className={styles.workspace}>
        <header className={styles.header}>
          <ActionIcon
            className={styles.mobileMenu}
            icon={Menu}
            onClick={() => setSidebarOpen(true)}
            title="打开导航"
          />
          <Dropdown
            disabled={
              applicationLoading || applications.length === 0 || streaming
            }
            menu={{
              items: applicationMenuItems,
              onClick: ({ key }) => setSelectedApplicationCode(key),
              selectedKeys: selectedApplicationCode
                ? [selectedApplicationCode]
                : [],
            }}
            placement="bottomLeft"
            trigger={["click"]}
          >
            <button
              aria-label="选择 AI 应用"
              className={styles.modelPicker}
              type="button"
            >
              <span className={styles.modelIcon}>
                <Bot size={17} />
              </span>
              <span>
                <strong>
                  {selectedApplication?.name ||
                    (applicationLoading
                      ? "正在加载应用…"
                      : "暂无可用应用")}
                </strong>
                <small>
                  {selectedApplication?.description ||
                    selectedApplication?.code ||
                    (applicationLoading
                      ? "请稍候"
                      : "请先在管理端发布应用")}
                </small>
              </span>
              <ChevronsUpDown size={14} />
            </button>
          </Dropdown>
          <div className={styles.headerActions}>
            <Tag color="blue" variant="filled">
              工作台预览
            </Tag>
            <ActionIcon
              aria-label={
                appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"
              }
              icon={appearance === "dark" ? Sun : Moon}
              onClick={toggleAppearance}
              size="small"
              title={
                appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"
              }
            />
            <ActionIcon
              aria-label="任务记录"
              className={styles.taskAction}
              icon={ListTodo}
              size="small"
              title="任务记录"
            />
            <ActionIcon
              aria-label="更多操作"
              icon={MoreHorizontal}
              size="small"
              title="更多操作"
            />
          </div>
        </header>

        <div className={styles.content}>
          {messages.length === 0 && !messageLoading ? (
            <section className={styles.hero}>
              <div className={styles.heroGlow} />
              <div className={styles.heroIcon}>
                <Image
                  alt=""
                  className={styles.heroLogo}
                  height={58}
                  priority
                  src="/lingxi-logo-glass.png"
                  style={{ height: "auto" }}
                  width={64}
                />
              </div>
              <p className={styles.eyebrow}>你的企业智能协作伙伴</p>
              <h1>你好，今天想一起完成什么？</h1>
              <p className={styles.subtitle}>
                连接知识、数据与工具，把复杂工作变成清晰可靠的结果。
              </p>

              <div className={styles.suggestionGrid}>
                {suggestions.map(
                  ({ description, icon: SuggestionIcon, title }) => (
                    <button
                      className={styles.suggestionCard}
                      key={title}
                      onClick={() => startTask(`${title}：`)}
                      type="button"
                    >
                      <span className={styles.suggestionIcon}>
                        <SuggestionIcon size={18} />
                      </span>
                      <span>
                        <strong>{title}</strong>
                        <small>{description}</small>
                      </span>
                      <Plus className={styles.suggestionArrow} size={16} />
                    </button>
                  ),
                )}
              </div>
            </section>
          ) : (
            <section className={styles.messageStage} aria-label="对话内容">
              <div className={styles.messageList}>
                {messageLoading && messages.length === 0 && (
                  <div className={styles.messageLoading}>
                    <Spin />
                  </div>
                )}
                {messages.length < messageTotal && (
                  <button
                    className={styles.loadOlder}
                    disabled={messageLoading}
                    onClick={() => void handleLoadOlder()}
                    type="button"
                  >
                    {messageLoading ? "正在加载…" : "加载更早消息"}
                  </button>
                )}
                {messages.map((message) => (
                  <article
                    className={`${styles.messageRow} ${
                      message.role === "user"
                        ? styles.userMessage
                        : styles.assistantMessage
                    }`}
                    key={message.id}
                  >
                    {message.role === "assistant" && (
                      <span
                        className={styles.assistantAvatar}
                        aria-hidden="true"
                      >
                        <Sparkles size={17} />
                      </span>
                    )}
                    <div className={styles.messageBody}>
                      <span className={styles.messageAuthor}>
                        {message.role === "user" ? "你" : "灵犀通用助手"}
                      </span>
                      {message.role === "assistant" &&
                      message.timeline?.length ? (
                        <ExecutionTimeline events={message.timeline} />
                      ) : null}
                      {message.role === "assistant" &&
                      message.subAgents?.length ? (
                        <SubAgentExecutionList
                          subAgents={message.subAgents}
                        />
                      ) : null}
                      {message.role === "assistant" &&
                      message.tools?.length ? (
                        <ToolExecutionList tools={message.tools} />
                      ) : null}
                      <div
                        aria-live={
                          message.role === "assistant" ? "polite" : undefined
                        }
                        className={styles.messageContent}
                      >
                        {message.content ? (
                          message.role === "assistant" ? (
                            <Markdown
                              animated={message.status === "streaming"}
                              componentProps={{ img: { preview: false } }}
                              enableImageGallery={false}
                              enableStream={message.status === "streaming"}
                              fullFeaturedCodeBlock
                              marginMultiple={0.5}
                              streamAnimationGranularity="word"
                              streamSmoothingPreset="realtime"
                              variant="chat"
                            >
                              {message.content}
                            </Markdown>
                          ) : (
                            message.content
                          )
                        ) : message.status === "streaming" ? (
                          <span
                            className={styles.thinking}
                            aria-label="正在生成回答"
                          >
                            <i />
                            <i />
                            <i />
                          </span>
                        ) : (
                          "暂未生成内容"
                        )}
                        {message.status === "streaming" &&
                          message.content && (
                            <span
                              className={styles.streamCaret}
                              aria-hidden="true"
                            />
                          )}
                      </div>
                      {message.status === "stopped" && (
                        <span className={styles.messageStatus}>
                          已停止生成
                        </span>
                      )}
                      {message.status === "error" && (
                        <div className={styles.messageError} role="alert">
                          <span>
                            {message.errorMessage ||
                              streamError ||
                              "生成失败，请稍后重试"}
                          </span>
                          <button onClick={retryLastPrompt} type="button">
                            <RotateCcw size={14} />
                            {resumeAvailable ? "继续接收" : "重新生成"}
                          </button>
                        </div>
                      )}
                    </div>
                  </article>
                ))}
                <div className={styles.messageEnd} ref={messageEndRef} />
              </div>
            </section>
          )}

          {/* 输入区 */}
          <div className={styles.composerWrap}>
            {pendingClarification && (
              <div
                className={styles.clarificationCard}
                data-testid="intent-clarification"
                role="group"
                aria-label={pendingClarification.interactionType === "RISK_CONFIRMATION" ? "操作确认" : "补充信息"}
              >
                <div>
                  <strong>{pendingClarification.interactionType === "RISK_CONFIRMATION" ? "确认操作" : "需要你确认一下"}</strong>
                  {pendingClarification.round > 0 && <span>第 {pendingClarification.round} 轮</span>}
                </div>
                <p>{pendingClarification.question}</p>
                {pendingClarification.options.length > 0 && (
                  <div className={styles.clarificationOptions}>
                    {pendingClarification.options.map((option) => (
                      <button
                        key={option.code}
                        disabled={streaming}
                        onClick={() =>
                          answerClarification(pendingClarification, option)
                        }
                        type="button"
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {conversationStatus === "ARCHIVED" && (
              <div className={styles.archivedNotice}>
                该会话已归档，仅支持查看历史消息
              </div>
            )}
            {(streamError || requestError) &&
              !messages.some((m) => m.status === "error") && (
                <button
                  className={styles.requestError}
                  onClick={() => {
                    clearError();
                    setRequestError(undefined);
                  }}
                  title="关闭提示"
                  type="button"
                >
                  {streamError || requestError}
                </button>
              )}
            <div className={styles.composer}>
              <ChatInputArea.Inner
                disabled={
                  conversationStatus === "ARCHIVED" ||
                  messageLoading ||
                  applicationLoading ||
                  !selectedApplicationCode
                }
                onChange={(event) => setInput(event.target.value)}
                onSend={() => void sendPrompt(input)}
                placeholder={
                  conversationStatus === "ARCHIVED"
                    ? "该会话已归档"
                    : applicationLoading
                      ? "正在加载可用应用…"
                      : !selectedApplicationCode
                        ? "请先在管理端发布可用应用"
                        : "向灵犀提问，或描述你想完成的工作…"
                }
                rows={3}
                value={input}
              />
              <div className={styles.composerToolbar}>
                <div>
                  <ActionIcon icon={Paperclip} size="small" title="添加附件" />
                  <ActionIcon
                    icon={Boxes}
                    size="small"
                    title="选择知识库"
                  />
                </div>
                <div className={styles.composerActions}>
                  <span>Enter 发送 · Shift + Enter 换行</span>
                  {streaming ? (
                    <button
                      aria-label="停止生成"
                      className={styles.stopButton}
                      onClick={() => void stopStreaming()}
                      title="停止生成"
                      type="button"
                    >
                      <Square fill="currentColor" size={11} strokeWidth={2.4} />
                    </button>
                  ) : (
                    <button
                      aria-label="发送消息"
                      className={styles.sendButton}
                      disabled={
                        !input.trim() ||
                        conversationStatus === "ARCHIVED" ||
                        messageLoading ||
                        applicationLoading ||
                        !selectedApplicationCode
                      }
                      onClick={() => void sendPrompt(input)}
                      title="发送消息"
                      type="button"
                    >
                      <ArrowUp size={17} strokeWidth={2.5} />
                    </button>
                  )}
                </div>
              </div>
            </div>
            <p className={styles.disclaimer}>
              AI 生成内容仅供参考，重要信息请核实。
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
