"use client";

import { ActionIcon, Avatar, Button, Markdown, Tag } from "@lobehub/ui";
import { ChatInputArea } from "@lobehub/ui/chat";
import CopyButton from "@lobehub/ui/es/CopyButton/index";
import { Drawer, Dropdown, Empty, Input, Spin } from "antd";
import type { MenuProps } from "antd";
import {
  Archive,
  ArrowUp,
  Bot,
  Boxes,
  ChevronsUpDown,
  History,
  LogOut,
  Menu,
  MessageSquareText,
  Moon,
  MoreHorizontal,
  Paperclip,
  Pencil,
  Pin,
  PinOff,
  Plus,
  RotateCcw,
  Search,
  Settings2,
  ShieldAlert,
  AlarmClock,
  Sparkles,
  Square,
  Sun,
  Trash2,
} from "lucide-react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  type AvailableApplication,
  type Conversation,
  ChatStreamError,
  listAvailableApplications,
} from "@/lib/chat";
import { useAuth } from "./auth-provider";
import { useThemeMode } from "./app-provider";
import { type ChatMessage, useChatStream } from "@/hooks/use-chat-stream";
import { useConversation } from "@/hooks/use-conversation";
import { useNotifications } from "@/hooks/use-notifications";
import { useTriggers } from "@/hooks/use-triggers";
import { listTriggerCapabilities, type TriggerCapabilities } from "@/lib/trigger";
import { usePinnedConversations } from "@/hooks/use-pinned-conversations";
import NotificationCenter from "./notification-center";
import TriggerDrawer from "./trigger-drawer";
import ExecutionTrace from "./execution-trace";
import styles from "./workbench-shell.module.css";

// ─── 子组件 ───────────────────────────────────────────────────

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
  assistant?: AvailableApplication["assistant"];
  content: string;
  id: string;
  origin?: string;
  role: string;
  status: string;
  errorCode?: string;
  errorMessage?: string;
}): ChatMessage | undefined {
  const role = msg.role === "USER" ? "user" : "assistant";
  if (msg.role === "SYSTEM") return undefined;
  let status: ChatMessage["status"] = "done";
  if (msg.status === "GENERATING") status = "streaming";
  else if (msg.status === "WAITING_HUMAN") status = "waiting";
  else if (msg.status === "FAILED" || msg.status === "INTERRUPTED")
    status = "error";
  else if (msg.status === "CANCELLED") status = "stopped";
  return {
    assistant: msg.assistant,
    content: msg.content,
    errorCode: msg.errorCode,
    errorMessage: msg.errorMessage,
    id: msg.id,
    origin: msg.origin === "TRIGGER" ? "TRIGGER" : undefined,
    role,
    status,
  };
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
    messageTotal,
    renamingId,
    renamingTitle,
    setConversationId,
    setConversationStatus,
    setHistoryOpen,
    setHistoryKeyword,
    refreshRecentConversations,
    loadHistoryConversations,
    selectConversation: loadConversation,
    loadOlderMessages,
    beginRename,
    cancelRename,
    submitRename,
    setRenamingTitle,
    archiveSelectedConversation,
    deleteSelectedConversation,
  } = useConversation({ session, onRequestError: handleRequestError });

  const {
    isPinned,
    pinnedConversations,
    removePinned,
    togglePinned,
    updatePinned,
  } = usePinnedConversations(session?.user.id);

  const loadedConversations = useMemo(
    () =>
      new Map(
        [...recentConversations, ...historyConversations].map(
          (conversation) => [conversation.id, conversation],
        ),
      ),
    [historyConversations, recentConversations],
  );
  const visiblePinnedConversations = useMemo(
    () =>
      pinnedConversations.map(
        (conversation) =>
          loadedConversations.get(conversation.id) || conversation,
      ),
    [loadedConversations, pinnedConversations],
  );
  const unpinnedRecentConversations = useMemo(
    () =>
      recentConversations.filter(
        (conversation) => !isPinned(conversation.id),
      ),
    [isPinned, recentConversations],
  );

  // ─── 对话流 Hook ───────────────────────────────────────────

  const handleLogout = useCallback(() => {
    logout();
    router.replace("/login");
  }, [logout, router]);

  const handleConversationCreated = useCallback((id: string) => {
    setConversationId(id);
    setConversationStatus("ACTIVE");
    // 首次发送已落库就刷新列表，不等待模型输出结束。
    void refreshRecentConversations();
  }, [setConversationId, setConversationStatus, refreshRecentConversations]);

  const handleOpenHistory = () => {
    setHistoryOpen(true);
    void loadHistoryConversations(1, historyKeyword);
  };

  const selectedApplication = applications.find(
    (app) => app.code === selectedApplicationCode,
  );

  const {
    messages,
    setMessages,
    streaming,
    streamError,
    pendingClarification,
    pendingInteraction,
    pendingSuggestions,
    interactionSubmitting,
    resumeAvailable,
    sendPrompt,
    stopStreaming,
    retryLastPrompt,
    answerClarification,
    answerInteraction,
    clearError,
    discardPendingGeneration,
    restorePendingInteraction,
  } = useChatStream({
    session,
    selectedAssistant: selectedApplication?.assistant,
    selectedApplicationCode,
    conversationId: currentConversationId,
    onConversationCreated: handleConversationCreated,
    onLogout: handleLogout,
  });

  const { refreshUnreadCount, unreadCount } = useNotifications({ session });

  const [triggerCapabilities, setTriggerCapabilities] = useState<TriggerCapabilities>();
  const [triggerDrawerOpen, setTriggerDrawerOpen] = useState(false);
  const {
    create: createTrigger,
    fireHistory,
    fireHistoryLoading,
    loadFireHistory,
    loadTriggers,
    loading: triggerLoading,
    pause: pauseTrigger,
    remove: removeTrigger,
    resume: resumeTrigger,
    triggers,
  } = useTriggers({
    onError: (message) => setRequestError(message),
    session,
  });

  const openTriggerDrawer = useCallback(() => {
    setTriggerDrawerOpen(true);
    void loadTriggers();
  }, [loadTriggers]);

  const handleLoadTriggerCapabilities = useCallback(async (code: string) => {
    if (!session) return;
    try {
      const capabilities = await listTriggerCapabilities(code, session);
      setTriggerCapabilities(capabilities);
    } catch (error) {
      handleRequestError(error, "获取可绑定能力失败，请稍后重试");
    }
  }, [handleRequestError, session]);

  useEffect(() => {
    if (ready && !session) {
      router.replace("/login");
    }
  }, [ready, router, session]);

  // ─── 应用选择 ──────────────────────────────────────────────

  useEffect(() => {
    if (!session) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setApplicationLoading(true);

    listAvailableApplications(session)
      .then((apps) => {
        setApplications(apps);
        setSelectedApplicationCode((current) =>
          current && apps.some((app) => app.code === current)
            ? current
            : (apps[0]?.code ?? null),
        );
      })
      .catch((error) => handleRequestError(error, "获取可用应用失败"))
      .finally(() => setApplicationLoading(false));
    }, [handleRequestError, session]);

  useEffect(() => {
    if (!session) return;
    void refreshRecentConversations();
  }, [session, refreshRecentConversations]);

  // ─── 会话选择（加载历史消息） ──────────────────────────────

  const handleSelectConversation = async (conversation: Conversation) => {
    if ((streaming || pendingClarification || pendingInteraction) && conversation.id !== currentConversationId) {
      clearError();
      return;
    }
    if (conversation.id !== currentConversationId) {
      discardPendingGeneration();
    }
    clearError();
    const records = await loadConversation(conversation);
    const historyMessages = records
      .toReversed()
      .map(toChatMessage)
      .filter((message): message is ChatMessage => message !== undefined);
    setMessages(historyMessages);
    try {
      await restorePendingInteraction(conversation.id, historyMessages);
    } catch (error) {
      handleRequestError(error, "恢复待确认操作失败");
    }
  };

  /** 通知点击只携带会话主键，按 ACTIVE 会话语义复用会话选择链路。 */
  const stubConversation = (conversationId: string): Conversation => ({
    createdAt: "",
    id: conversationId,
    messageCount: 0,
    status: "ACTIVE",
    title: "",
    updatedAt: "",
  });

  // ─── 加载更早消息 ─────────────────────────────────────────

  const handleLoadOlder = async () => {
    if (!currentConversationId || !session) return;
    const records = await loadOlderMessages();
    const olderMessages = records
      .toReversed()
      .map(toChatMessage)
      .filter((message): message is ChatMessage => message !== undefined);
    if (olderMessages.length > 0) {
      setMessages((current) => [...olderMessages, ...current]);
    }
  };

  const handleSend = async () => {
    const submittedInput = input;
    const accepted = await sendPrompt(submittedInput);
    if (accepted) {
      setInput((current) => (current === submittedInput ? "" : current));
    }
  };

  const startNewChat = () => {
    if (streaming || pendingInteraction) return;
    discardPendingGeneration();
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

  const isNewConversation = messages.length === 0 && !messageLoading;
  const conversationEntering =
    messages.length <= 2 && messages.some((message) => message.animateOnMount);

  // ─── 渲染会话项 ───────────────────────────────────────────

  const handleRename = async (conversation: Conversation) => {
    const title = await submitRename(conversation);
    if (title) updatePinned({ ...conversation, title });
  };

  const handleArchive = async (conversation: Conversation) => {
    if (await archiveSelectedConversation(conversation)) {
      removePinned(conversation.id);
    }
  };

  const handleDelete = async (conversation: Conversation) => {
    if (await deleteSelectedConversation(conversation)) {
      removePinned(conversation.id);
    }
  };

  const renderConversation = (
    conversation: Conversation,
    showStatus = false,
    pinnedOverride?: boolean,
  ) => {
    const selected = conversation.id === currentConversationId;
    const pinned = pinnedOverride ?? isPinned(conversation.id);
    const menu: MenuProps = {
      items: [
        { icon: <Pencil size={14} />, key: "rename", label: "重命名" },
        ...(conversation.status === "ACTIVE" || pinned
          ? [
              {
                icon: pinned ? <PinOff size={14} /> : <Pin size={14} />,
                key: "pin",
                label: pinned ? "取消置顶" : "置顶",
              },
            ]
          : []),
        ...(conversation.status === "ACTIVE"
          ? [
              {
                icon: <Archive size={14} />,
                key: "archive",
                label: "归档",
              },
            ]
          : []),
        {
          danger: true,
          icon: <Trash2 size={14} />,
          key: "delete",
          label: "删除",
        },
      ],
      onClick: ({ domEvent, key }) => {
        domEvent.stopPropagation();
        if (key === "rename") beginRename(conversation);
        if (key === "pin") togglePinned(conversation);
        if (key === "archive") void handleArchive(conversation);
        if (key === "delete") void handleDelete(conversation);
      },
    };
    return (
      <Dropdown
        key={conversation.id}
        menu={menu}
        placement="bottomLeft"
        trigger={["contextMenu"]}
      >
        <div
          className={`${styles.conversation} ${selected ? styles.conversationActive : ""}`}
        >
          {renamingId === conversation.id ? (
            <div className={styles.conversationSelect}>
              {pinned ? <Pin size={15} /> : <MessageSquareText size={16} />}
              <span>
                <input
                  autoFocus
                  className={styles.conversationRename}
                  maxLength={100}
                  onBlur={() => void handleRename(conversation)}
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
              onClick={() => void handleSelectConversation(conversation)}
              type="button"
            >
              {pinned ? <Pin size={15} /> : <MessageSquareText size={16} />}
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
          <Dropdown menu={menu} placement="bottomRight" trigger={["click"]}>
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
      </Dropdown>
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
              height={581}
              priority
              src="/lingxi-logo.png"
              width={640}
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
          {visiblePinnedConversations.length > 0 ? (
            visiblePinnedConversations.map((conversation) =>
              renderConversation(conversation, false, true),
            )
          ) : (
            <span className={styles.conversationEmpty}>暂无置顶对话</span>
          )}
        </div>

        <div
          className={`${styles.historyHeader} ${styles.recentHistoryHeader}`}
        >
          <span>最近对话</span>
          <ActionIcon
            icon={Search}
            onClick={handleOpenHistory}
            size="small"
            title="搜索对话"
          />
        </div>
        <div className={styles.conversationList}>
          {conversationLoading ? (
            <div className={styles.conversationState}>
              <Spin size="small" />
            </div>
          ) : unpinnedRecentConversations.length > 0 ? (
            unpinnedRecentConversations.map((conv) => renderConversation(conv))
          ) : (
            <span className={styles.conversationEmpty}>暂无会话记录</span>
          )}
        </div>

        <div className={styles.sidebarFooter}>
          <button
            className={styles.navItem}
            onClick={handleOpenHistory}
            type="button"
          >
            <History size={17} />
            全部历史
          </button>
          <button className={styles.navItem} onClick={openTriggerDrawer} type="button">
            <AlarmClock size={17} />
            定时任务
          </button>
          <button className={styles.navItem} type="button">
            <Settings2 size={17} />
            设置
          </button>
          <div className={styles.profile}>
            <Avatar
              avatar={session.user.avatarUrl}
              background={appearance === "dark" ? "#25324a" : "#e8f1ff"}
              size={34}
              title={session.user.displayName}
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

      {/* 定时任务抽屉 */}
      <TriggerDrawer
        applicationCode={selectedApplicationCode}
        onClose={() => setTriggerDrawerOpen(false)}
        onCreate={createTrigger}
        onLoadCapabilities={handleLoadTriggerCapabilities}
        onLoadFireHistory={loadFireHistory}
        onPause={pauseTrigger}
        onRemove={removeTrigger}
        onResume={resumeTrigger}
        open={triggerDrawerOpen}
        triggerCapabilities={triggerCapabilities}
        triggerFires={fireHistory}
        triggerFiresLoading={fireHistoryLoading}
        triggerLoading={triggerLoading}
        triggers={triggers}
      />

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
            <NotificationCenter
              onOpenConversation={(conversationId) =>
                void handleSelectConversation(stubConversation(conversationId))
              }
              onMarkedRead={refreshUnreadCount}
              onRequestError={handleRequestError}
              session={session}
              unreadCount={unreadCount}
            />
            <ActionIcon
              aria-label="更多操作"
              icon={MoreHorizontal}
              size="small"
              title="更多操作"
            />
          </div>
        </header>

        <div
          className={`${styles.content} ${
            isNewConversation ? styles.newConversationContent : ""
          } ${conversationEntering ? styles.conversationEntering : ""}`}
        >
          {isNewConversation ? (
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
                    } ${message.animateOnMount ? styles.messageFlyIn : ""}`}
                    key={message.id}
                  >
                    {message.role === "assistant" && (
                      <Avatar
                        avatar={message.assistant?.avatar}
                        background={appearance === "dark" ? "#25324a" : "#e8f1ff"}
                        className={styles.messageAvatar}
                        shape="square"
                        size={34}
                        title={message.assistant?.name || "应用助手"}
                        variant="filled"
                      />
                    )}
                    <div className={styles.messageBody}>
                      <span className={styles.messageAuthor}>
                        {message.role === "user"
                          ? "你"
                          : (message.assistant?.name || "应用助手")}
                        {message.origin === "TRIGGER" && (
                          <Tag className={styles.triggerBadge} color="blue">
                            <AlarmClock aria-hidden="true" size={11} />
                            定时任务
                          </Tag>
                        )}
                      </span>
                      {message.role === "assistant" &&
                      (message.status === "streaming" ||
                        message.timeline?.length ||
                        message.progress?.length ||
                        message.subAgents?.length ||
                        message.stepOutputs?.length ||
                        message.tools?.length) ? (
                        <ExecutionTrace
                          key={message.status === "streaming" ? "running" : "settled"}
                          errorMessage={message.errorMessage}
                          failed={message.status === "error"}
                          streaming={message.status === "streaming"}
                          progress={message.progress}
                          subAgents={message.subAgents}
                          stepOutputs={message.stepOutputs}
                          stopped={message.status === "stopped"}
                          timeline={message.timeline}
                          tools={message.tools}
                          waiting={message.status === "waiting"}
                        />
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
                        ) : message.status === "streaming"
                          || message.status === "waiting"
                          || message.status === "error"
                          || message.status === "stopped" ? (
                          null
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
                      {message.content ? (
                        <div className={styles.messageActions}>
                          <CopyButton
                            aria-label={
                              message.role === "user" ? "复制你的消息" : "复制助手回复"
                            }
                            className={styles.copyMessage}
                            content={message.content}
                            size={{ blockSize: 32, size: 15 }}
                            title="复制内容"
                            variant="borderless"
                          />
                        </div>
                      ) : null}
                      {message.status === "stopped" && (
                        <span className={styles.messageStatus}>
                          {message.errorMessage || "已停止生成"}
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
                    {message.role === "user" && (
                      <Avatar
                        avatar={session.user.avatarUrl}
                        background={appearance === "dark" ? "#25324a" : "#e8f1ff"}
                        className={styles.messageAvatar}
                        shape="square"
                        size={34}
                        title={session.user.displayName}
                        variant="filled"
                      />
                    )}
                  </article>
                ))}
                <div className={styles.messageEnd} ref={messageEndRef} />
              </div>
            </section>
          )}

          {/* 输入区 */}
          <div
            className={`${styles.composerWrap} ${
              isNewConversation ? styles.newConversationComposer : ""
            }`}
          >
            {pendingClarification && (
              <div
                className={styles.clarificationCard}
                data-testid="intent-clarification"
                role="group"
                aria-label="补充信息"
              >
                <div>
                  <strong>需要你确认一下</strong>
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
            {pendingInteraction && (
              <section
                aria-label="操作审批"
                className={styles.approvalCard}
                data-risk={pendingInteraction.riskLevel.toLowerCase()}
                data-testid="tool-approval"
              >
                <div className={styles.approvalHeader}>
                  <span className={styles.approvalIcon} aria-hidden="true">
                    <ShieldAlert size={17} />
                  </span>
                  <div>
                    <strong>{pendingInteraction.title}</strong>
                    <span>{pendingInteraction.riskLevel === "CRITICAL" ? "严重风险" : pendingInteraction.riskLevel === "HIGH" ? "高风险" : pendingInteraction.riskLevel === "MEDIUM" ? "中风险" : "低风险"}</span>
                  </div>
                </div>
                <p>{pendingInteraction.summary}</p>
                <div className={styles.approvalMeta}>
                  {pendingInteraction.resourceScope && <span>作用范围：{pendingInteraction.resourceScope}</span>}
                  <span>有效期至 {new Date(pendingInteraction.expiresAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}</span>
                </div>
                <div className={styles.approvalActions}>
                  <button
                    disabled={interactionSubmitting}
                    onClick={() => void answerInteraction(pendingInteraction, "REJECT")}
                    type="button"
                  >
                    拒绝
                  </button>
                  <button
                    className={styles.approveButton}
                    disabled={interactionSubmitting}
                    onClick={() => void answerInteraction(pendingInteraction, "APPROVE")}
                    type="button"
                  >
                    {interactionSubmitting ? "正在提交…" : "允许本次操作"}
                  </button>
                </div>
              </section>
            )}
            {pendingSuggestions.length > 0 && !streaming && !pendingInteraction && (
              <div
                aria-label="后续建议"
                className={styles.suggestionChips}
                data-testid="answer-suggestions"
                role="group"
              >
                <span className={styles.suggestionChipsLabel}>
                  <Sparkles aria-hidden="true" size={14} />
                  接下来可以
                </span>
                <div className={styles.suggestionChipsList}>
                  {pendingSuggestions.map((suggestion) => (
                    <button
                      key={suggestion.id}
                      disabled={streaming}
                      onClick={() => void sendPrompt(suggestion.prompt)}
                      title={suggestion.prompt}
                      type="button"
                    >
                      {suggestion.title}
                    </button>
                  ))}
                </div>
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
                  Boolean(pendingInteraction) ||
                  !selectedApplicationCode
                }
                loading={streaming}
                onInput={setInput}
                onSend={() => void handleSend()}
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
                        Boolean(pendingInteraction) ||
                        !selectedApplicationCode
                      }
                      onClick={() => void handleSend()}
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
