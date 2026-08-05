"use client";

import { ActionIcon, Avatar, Button, Markdown, Tag } from "@lobehub/ui";
import { ChatInputArea } from "@lobehub/ui/chat";
import { Drawer, Dropdown, Empty, Input, Spin } from "antd";
import {
  Archive,
  ArrowUp,
  Bot,
  Boxes,
  CircleCheck,
  CircleX,
  ChevronsUpDown,
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
import { useEffect, useRef, useState } from "react";
import {
  archiveConversation,
  type AvailableApplication,
  type ChatClarificationEvent,
  type ChatSubAgentEvent,
  type ChatToolEvent,
  type ClarificationResponse,
  cancelChat,
  ChatStreamError,
  createConversation,
  deleteConversation,
  type Conversation,
  type ConversationMessage,
  listConversationMessages,
  listConversations,
  listAvailableApplications,
  renameConversation,
  resumeChat,
  streamChat,
} from "@/lib/chat";
import { normalizeMarkdownTables } from "@/lib/markdown";
import { useAuth } from "./auth-provider";
import { useThemeMode } from "./app-provider";
import styles from "./workbench-shell.module.css";

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

type ChatMessage = {
  content: string;
  errorMessage?: string;
  id: string;
  role: "assistant" | "user";
  status?: "done" | "error" | "stopped" | "streaming";
  subAgents?: ChatSubAgentEvent[];
  tools?: ChatToolEvent[];
};

type PendingGeneration = {
  answer: string;
  assistantMessageId: string;
  conversationId: string;
  lastSequence: number;
  prompt: string;
  clarification?: ChatClarificationEvent;
  subAgents: ChatSubAgentEvent[];
  tools: ChatToolEvent[];
  userMessageId: string;
};

const PENDING_GENERATION_KEY = "lingxi-pending-generation";

function readPendingGeneration(): PendingGeneration | undefined {
  try {
    const value = sessionStorage.getItem(PENDING_GENERATION_KEY);
    if (!value) return undefined;
    const pending = JSON.parse(value) as PendingGeneration;
    return { ...pending, subAgents: pending.subAgents ?? [], tools: pending.tools ?? [] };
  } catch {
    sessionStorage.removeItem(PENDING_GENERATION_KEY);
    return undefined;
  }
}

function mergeToolEvents(events: ChatToolEvent[] | undefined, nextEvent: ChatToolEvent) {
  const currentEvents = events ?? [];
  const existing = currentEvents.some((event) => event.invocationId === nextEvent.invocationId);
  if (!existing) return [...currentEvents, nextEvent];
  return currentEvents.map((event) => event.invocationId === nextEvent.invocationId ? nextEvent : event);
}

function mergeSubAgentEvents(
  events: ChatSubAgentEvent[] | undefined,
  nextEvent: ChatSubAgentEvent,
) {
  const currentEvents = events ?? [];
  const existing = currentEvents.some(
    (event) => event.delegationId === nextEvent.delegationId,
  );
  if (!existing) return [...currentEvents, nextEvent];
  return currentEvents.map((event) =>
    event.delegationId === nextEvent.delegationId ? nextEvent : event,
  );
}

function SubAgentExecutionList({ subAgents }: { subAgents: ChatSubAgentEvent[] }) {
  return (
    <div
      className={styles.subAgentList}
      aria-label="子智能体执行状态"
      data-testid="subagent-execution-list"
    >
      {subAgents.map((subAgent) => (
        <div
          className={`${styles.subAgentItem} ${styles[`subAgent${subAgent.status}`]}`}
          key={subAgent.delegationId}
        >
          <span className={styles.subAgentStatusIcon} aria-hidden="true">
            {subAgent.status === "RUNNING" && <LoaderCircle size={14} />}
            {subAgent.status === "SUCCEEDED" && <CircleCheck size={14} />}
            {subAgent.status === "FAILED" && <CircleX size={14} />}
          </span>
          <Bot size={14} aria-hidden="true" />
          <span>{subAgent.agentName}</span>
          <small>
            {subAgent.status === "RUNNING" && "处理中"}
            {subAgent.status === "SUCCEEDED" &&
              `已完成${subAgent.durationMillis === undefined ? "" : ` · ${subAgent.durationMillis}ms`}`}
            {subAgent.status === "FAILED" && "处理失败"}
          </small>
        </div>
      ))}
    </div>
  );
}

function ToolExecutionList({ tools }: { tools: ChatToolEvent[] }) {
  return (
    <div className={styles.toolList} aria-label="工具调用状态">
      {tools.map((tool) => (
        <div className={`${styles.toolItem} ${styles[`tool${tool.status}`]}`} key={tool.invocationId}>
          <span className={styles.toolIcon} aria-hidden="true">
            {tool.status === "RUNNING" && <LoaderCircle size={14} />}
            {tool.status === "SUCCEEDED" && <CircleCheck size={14} />}
            {tool.status === "FAILED" && <CircleX size={14} />}
          </span>
          <Wrench size={13} aria-hidden="true" />
          <span>{tool.displayName}</span>
          <small>
            {tool.status === "RUNNING" && "调用中"}
            {tool.status === "SUCCEEDED" && `已完成${tool.durationMillis === undefined ? "" : ` · ${tool.durationMillis}ms`}`}
            {tool.status === "FAILED" && "调用失败"}
          </small>
        </div>
      ))}
    </div>
  );
}

function savePendingGeneration(value: PendingGeneration) {
  sessionStorage.setItem(PENDING_GENERATION_KEY, JSON.stringify(value));
}

function clearPendingGeneration() {
  sessionStorage.removeItem(PENDING_GENERATION_KEY);
}

function createMessageId(role: ChatMessage["role"]) {
  return `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function toChatMessage(message: ConversationMessage): ChatMessage | undefined {
  if (message.role === "SYSTEM") return undefined;
  const status: ChatMessage["status"] =
    message.status === "COMPLETED"
      ? "done"
      : message.status === "FAILED"
        ? "error"
        : message.status === "GENERATING"
          ? "streaming"
          : "stopped";
  return {
    content: message.content,
    errorMessage: message.errorMessage,
    id: message.id,
    role: message.role === "USER" ? "user" : "assistant",
    status,
  };
}

function formatConversationTime(value?: string) {
  if (!value) return "暂无消息";
  const time = new Date(value);
  const now = new Date();
  const difference = now.getTime() - time.getTime();
  if (difference >= 0 && difference < 60_000) return "刚刚";
  if (difference >= 0 && difference < 3_600_000) {
    return `${Math.max(1, Math.floor(difference / 60_000))} 分钟前`;
  }
  if (time.toDateString() === now.toDateString()) {
    return time.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  }
  return time.toLocaleDateString("zh-CN", {
    day: "2-digit",
    month: "2-digit",
    year: time.getFullYear() === now.getFullYear() ? undefined : "numeric",
  });
}

export function WorkbenchShell() {
  const router = useRouter();
  const { logout, ready, session } = useAuth();
  const { appearance, toggleAppearance } = useThemeMode();
  const [input, setInput] = useState("");
  const [applications, setApplications] = useState<AvailableApplication[]>([]);
  const [applicationLoading, setApplicationLoading] = useState(true);
  const [selectedApplicationCode, setSelectedApplicationCode] = useState<string>();
  const [conversationId, setConversationId] = useState<string>();
  const [conversationStatus, setConversationStatus] = useState<Conversation["status"]>();
  const [recentConversations, setRecentConversations] = useState<Conversation[]>([]);
  const [conversationLoading, setConversationLoading] = useState(true);
  const [historyConversations, setHistoryConversations] = useState<Conversation[]>([]);
  const [historyCurrent, setHistoryCurrent] = useState(1);
  const [historyKeyword, setHistoryKeyword] = useState("");
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [messageCurrent, setMessageCurrent] = useState(1);
  const [messageLoading, setMessageLoading] = useState(false);
  const [messageTotal, setMessageTotal] = useState(0);
  const [renamingId, setRenamingId] = useState<string>();
  const [renamingTitle, setRenamingTitle] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingClarification, setPendingClarification] =
    useState<ChatClarificationEvent>();
  const [resumeAvailable, setResumeAvailable] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [streamError, setStreamError] = useState<string>();
  const [streaming, setStreaming] = useState(false);
  const activeAssistantIdRef = useRef<string | undefined>(undefined);
  const abortControllerRef = useRef<AbortController | null>(null);
  const messageEndRef = useRef<HTMLDivElement | null>(null);
  const pendingGenerationRef = useRef<PendingGeneration | undefined>(undefined);
  const recoveryStartedRef = useRef(false);

  const startTask = (prompt: string) => {
    setInput(prompt);
  };

  useEffect(() => {
    if (ready && !session) router.replace("/login");
  }, [ready, router, session]);

  useEffect(() => {
    if (!ready || !session) return;
    listConversations(session, { current: 1, size: 20, status: "ACTIVE" })
      .then((result) => setRecentConversations(result.records))
      .catch((error) => {
        if (error instanceof ChatStreamError && error.status === 401) {
          logout();
          router.replace("/login");
          return;
        }
        setStreamError(
          error instanceof ChatStreamError ? error.message : "查询最近会话失败",
        );
      })
      .finally(() => setConversationLoading(false));
  }, [logout, ready, router, session]);

  useEffect(() => {
    if (!ready || !session) return;
    listAvailableApplications(session)
      .then((availableApplications) => {
        setApplications(availableApplications);
        setSelectedApplicationCode((currentCode) => {
          const currentApplicationExists = availableApplications.some(
            (application) => application.code === currentCode,
          );
          if (currentApplicationExists) return currentCode;
          return availableApplications[0]?.code;
        });
      })
      .catch((error) => {
        if (error instanceof ChatStreamError && error.status === 401) {
          logout();
          router.replace("/login");
          return;
        }
        setStreamError(
          error instanceof ChatStreamError ? error.message : "查询可用应用失败",
        );
      })
      .finally(() => setApplicationLoading(false));
  }, [logout, ready, router, session]);

  useEffect(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    messageEndRef.current?.scrollIntoView({
      behavior: reduceMotion || streaming ? "auto" : "smooth",
      block: "end",
    });
  }, [messages, streaming]);

  useEffect(() => () => abortControllerRef.current?.abort(), []);

  useEffect(() => {
    if (!ready || !session || recoveryStartedRef.current) return;
    recoveryStartedRef.current = true;
    const pending = readPendingGeneration();
    if (!pending) return;

    pendingGenerationRef.current = pending;
    activeAssistantIdRef.current = pending.assistantMessageId;
    let disposed = false;
    queueMicrotask(() => {
      if (disposed) return;
      setConversationId(pending.conversationId);
      setConversationStatus("ACTIVE");
      setPendingClarification(pending.clarification);
      setMessages([
        {
          content: pending.prompt,
          id: pending.userMessageId,
          role: "user",
          status: "done",
        },
        {
          content: pending.answer,
          id: pending.assistantMessageId,
          role: "assistant",
          status: "streaming",
          subAgents: pending.subAgents,
          tools: pending.tools,
        },
      ]);
      setStreaming(true);
    });

    const controller = new AbortController();
    abortControllerRef.current = controller;
    let cancelled = false;
    void resumeChat({
      afterSequence: pending.lastSequence,
      assistantMessageId: pending.assistantMessageId,
      onCancelled: () => {
        cancelled = true;
        setMessages((current) =>
          current.map((message) =>
            message.id === pending.assistantMessageId
              ? { ...message, status: "stopped" }
              : message,
          ),
        );
      },
      onChunk: (content, sequence) => {
        const current = pendingGenerationRef.current;
        if (current) {
          current.answer += content;
          current.lastSequence = sequence;
          savePendingGeneration(current);
        }
        setMessages((messages) =>
          messages.map((message) =>
            message.id === pending.assistantMessageId
              ? { ...message, content: message.content + content }
              : message,
          ),
        );
      },
      onClarification: (clarification) => {
        const current = pendingGenerationRef.current;
        if (current) {
          current.clarification = clarification;
          current.lastSequence = clarification.sequence;
          savePendingGeneration(current);
        }
        setPendingClarification(clarification);
      },
      onSnapshot: (content) => {
        const current = pendingGenerationRef.current;
        if (current) {
          current.answer = content;
          savePendingGeneration(current);
        }
        setMessages((messages) =>
          messages.map((message) =>
            message.id === pending.assistantMessageId
              ? { ...message, content }
              : message,
          ),
        );
      },
      onSubAgent: (subAgent) => {
        const current = pendingGenerationRef.current;
        if (current) {
          current.subAgents = mergeSubAgentEvents(current.subAgents, subAgent);
          current.lastSequence = subAgent.sequence;
          savePendingGeneration(current);
        }
        setMessages((messages) =>
          messages.map((message) =>
            message.id === pending.assistantMessageId
              ? {
                  ...message,
                  subAgents: mergeSubAgentEvents(message.subAgents, subAgent),
                }
              : message,
          ),
        );
      },
      onTool: (tool) => {
        const current = pendingGenerationRef.current;
        if (current) {
          current.tools = mergeToolEvents(current.tools, tool);
          current.lastSequence = tool.sequence;
          savePendingGeneration(current);
        }
        setMessages((messages) =>
          messages.map((message) =>
            message.id === pending.assistantMessageId
              ? { ...message, tools: mergeToolEvents(message.tools, tool) }
              : message,
          ),
        );
      },
      session,
      signal: controller.signal,
    })
      .then(() => {
        if (disposed) return;
        if (!cancelled) {
          setMessages((current) =>
            current.map((message) =>
              message.id === pending.assistantMessageId
                ? { ...message, status: "done" }
                : message,
            ),
          );
        }
        clearPendingGeneration();
        pendingGenerationRef.current = undefined;
        setResumeAvailable(false);
      })
      .catch((error) => {
        if (disposed) return;
        if (error instanceof DOMException && error.name === "AbortError") return;
        const message =
          error instanceof ChatStreamError ? error.message : "恢复生成任务失败，请稍后刷新";
        setStreamError(message);
        setMessages((current) =>
          current.map((item) =>
            item.id === pending.assistantMessageId ? { ...item, status: "error" } : item,
          ),
        );
        if (error instanceof ChatStreamError && error.status === 401) {
          clearPendingGeneration();
          pendingGenerationRef.current = undefined;
          logout();
          router.replace("/login");
        } else if (
          error instanceof ChatStreamError &&
          (error.code === "AI_MODEL_ERROR" || error.status === 404)
        ) {
          clearPendingGeneration();
          pendingGenerationRef.current = undefined;
        } else {
          setResumeAvailable(true);
        }
      })
      .finally(() => {
        if (abortControllerRef.current === controller) abortControllerRef.current = null;
        if (!disposed) setStreaming(false);
      });
    return () => {
      disposed = true;
      controller.abort();
    };
  }, [logout, ready, router, session]);

  if (!ready || !session) {
    return (
      <main className={styles.authLoading} data-theme={appearance}>
        <span className={styles.authLoadingMark}>
          <Sparkles size={21} />
        </span>
        <p>正在进入灵析工作台…</p>
      </main>
    );
  }

  const handleRequestError = (error: unknown, fallback: string) => {
    if (error instanceof ChatStreamError && error.status === 401) {
      logout();
      router.replace("/login");
      return;
    }
    setStreamError(error instanceof ChatStreamError ? error.message : fallback);
  };

  const refreshRecentConversations = async () => {
    try {
      const result = await listConversations(session, {
        current: 1,
        size: 20,
        status: "ACTIVE",
      });
      setRecentConversations(result.records);
    } catch (error) {
      handleRequestError(error, "刷新最近会话失败");
    }
  };

  const loadHistoryConversations = async (
    current: number,
    keyword: string,
    append = false,
  ) => {
    setHistoryLoading(true);
    try {
      const result = await listConversations(session, {
        current,
        keyword,
        size: 20,
      });
      setHistoryConversations((items) =>
        append ? [...items, ...result.records] : result.records,
      );
      setHistoryCurrent(result.current);
      setHistoryTotal(result.total);
    } catch (error) {
      handleRequestError(error, "查询全部历史失败");
    } finally {
      setHistoryLoading(false);
    }
  };

  const openHistory = () => {
    setHistoryOpen(true);
    setHistoryKeyword("");
    void loadHistoryConversations(1, "");
  };

  const selectConversation = async (conversation: Conversation) => {
    if (streaming && conversation.id !== conversationId) {
      setStreamError("当前回答仍在生成，请先停止生成再切换会话");
      return;
    }
    setHistoryOpen(false);
    setSidebarOpen(false);
    setStreamError(undefined);
    setPendingClarification(undefined);
    setConversationId(conversation.id);
    setConversationStatus(conversation.status);
    setMessageLoading(true);
    try {
      const result = await listConversationMessages(conversation.id, session, {
        current: 1,
        size: 50,
      });
      const historyMessages = result.records
        .toReversed()
        .map(toChatMessage)
        .filter((message): message is ChatMessage => message !== undefined);
      setMessages(historyMessages);
      setMessageCurrent(result.current);
      setMessageTotal(result.total);
    } catch (error) {
      handleRequestError(error, "加载历史消息失败");
    } finally {
      setMessageLoading(false);
    }
  };

  const loadOlderMessages = async () => {
    if (!conversationId || messageLoading || messages.length >= messageTotal) return;
    setMessageLoading(true);
    try {
      const result = await listConversationMessages(conversationId, session, {
        current: messageCurrent + 1,
        size: 50,
      });
      const olderMessages = result.records
        .toReversed()
        .map(toChatMessage)
        .filter((message): message is ChatMessage => message !== undefined);
      setMessages((items) => [...olderMessages, ...items]);
      setMessageCurrent(result.current);
      setMessageTotal(result.total);
    } catch (error) {
      handleRequestError(error, "加载更早消息失败");
    } finally {
      setMessageLoading(false);
    }
  };

  const beginRename = (conversation: Conversation) => {
    setRenamingId(conversation.id);
    setRenamingTitle(conversation.title);
  };

  const submitRename = async (conversation: Conversation) => {
    const title = renamingTitle.trim();
    setRenamingId(undefined);
    if (!title || title === conversation.title) return;
    try {
      await renameConversation(conversation.id, title, session);
      const replaceTitle = (item: Conversation) =>
        item.id === conversation.id ? { ...item, title } : item;
      setRecentConversations((items) => items.map(replaceTitle));
      setHistoryConversations((items) => items.map(replaceTitle));
    } catch (error) {
      handleRequestError(error, "修改会话标题失败");
    }
  };

  const archiveSelectedConversation = async (conversation: Conversation) => {
    if (!window.confirm(`确认归档会话“${conversation.title}”吗？`)) return;
    try {
      await archiveConversation(conversation.id, session);
      setRecentConversations((items) => items.filter((item) => item.id !== conversation.id));
      setHistoryConversations((items) =>
        items.map((item) =>
          item.id === conversation.id ? { ...item, status: "ARCHIVED" } : item,
        ),
      );
      if (conversation.id === conversationId) setConversationStatus("ARCHIVED");
    } catch (error) {
      handleRequestError(error, "归档会话失败");
    }
  };

  const deleteSelectedConversation = async (conversation: Conversation) => {
    if (!window.confirm(`确认删除会话“${conversation.title}”吗？删除后不可恢复。`)) return;
    try {
      await deleteConversation(conversation.id, session);
      setRecentConversations((items) => items.filter((item) => item.id !== conversation.id));
      setHistoryConversations((items) => items.filter((item) => item.id !== conversation.id));
      setHistoryTotal((total) => Math.max(0, total - 1));
      if (conversation.id === conversationId) startNewChat();
    } catch (error) {
      handleRequestError(error, "删除会话失败");
    }
  };

  const handleLogout = () => {
    abortControllerRef.current?.abort();
    clearPendingGeneration();
    logout();
    router.replace("/login");
  };

  const startNewChat = () => {
    abortControllerRef.current?.abort();
    clearPendingGeneration();
    pendingGenerationRef.current = undefined;
    activeAssistantIdRef.current = undefined;
    setInput("");
    setConversationId(undefined);
    setConversationStatus(undefined);
    setMessages([]);
    setPendingClarification(undefined);
    setMessageCurrent(1);
    setMessageTotal(0);
    setResumeAvailable(false);
    setStreamError(undefined);
    setStreaming(false);
  };

  const stopStreaming = async () => {
    const assistantMessageId = activeAssistantIdRef.current;
    if (!assistantMessageId || !session) {
      abortControllerRef.current?.abort();
      setStreaming(false);
      return;
    }
    try {
      await cancelChat(assistantMessageId, session);
    } catch (error) {
      setStreamError(
        error instanceof ChatStreamError ? error.message : "停止生成失败，请稍后重试",
      );
      return;
    }
    abortControllerRef.current?.abort();
    setMessages((current) =>
      current.map((message) =>
        message.id === assistantMessageId ? { ...message, status: "stopped" } : message,
      ),
    );
    clearPendingGeneration();
    pendingGenerationRef.current = undefined;
    setResumeAvailable(false);
    setStreaming(false);
  };

  const sendPrompt = async (
    prompt: string,
    explicitClarificationResponse?: ClarificationResponse,
  ) => {
    const normalizedPrompt = prompt.trim();
    if (!normalizedPrompt || streaming || !session) return;
    if (!selectedApplicationCode) {
      setStreamError("请先选择一个已发布的 AI 应用");
      return;
    }
    if (conversationStatus === "ARCHIVED") {
      setStreamError("当前会话已归档，请新建对话后继续提问");
      return;
    }
    const clarificationResponse =
      explicitClarificationResponse ??
      (pendingClarification
        ? {
            clarificationId: pendingClarification.clarificationId,
            value: normalizedPrompt,
          }
        : undefined);
    setPendingClarification(undefined);

    const assistantId = createMessageId("assistant");
    let activeAssistantId = assistantId;
    let cancelled = false;
    const userMessageId = createMessageId("user");
    const nextMessages: ChatMessage[] = [
      {
        content: normalizedPrompt,
        id: userMessageId,
        role: "user",
        status: "done",
      },
      {
        content: "",
        id: assistantId,
        role: "assistant",
        status: "streaming",
        subAgents: [],
        tools: [],
      },
    ];

    setMessages((current) => [...current, ...nextMessages]);
    setInput("");
    setResumeAvailable(false);
    setStreamError(undefined);
    setStreaming(true);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      let activeConversationId = conversationId;
      if (!activeConversationId) {
        const conversation = await createConversation(session);
        activeConversationId = conversation.id;
        setConversationId(conversation.id);
        setConversationStatus(conversation.status);
      }
      await streamChat({
        applicationCode: selectedApplicationCode,
        clarificationResponse,
        clientMessageId: crypto.randomUUID(),
        conversationId: activeConversationId,
        onChunk: (content, sequence) => {
          const pending = pendingGenerationRef.current;
          if (pending) {
            pending.answer += content;
            pending.lastSequence = sequence;
            savePendingGeneration(pending);
          }
          setMessages((current) =>
            current.map((message) =>
              message.id === activeAssistantId
                ? { ...message, content: message.content + content }
                : message,
            ),
          );
        },
        onClarification: (clarification) => {
          const pending = pendingGenerationRef.current;
          if (pending) {
            pending.clarification = clarification;
            pending.lastSequence = clarification.sequence;
            savePendingGeneration(pending);
          }
          setPendingClarification(clarification);
        },
        onStart: (start) => {
          activeAssistantId = start.assistantMessageId;
          activeAssistantIdRef.current = start.assistantMessageId;
          pendingGenerationRef.current = {
            answer: "",
            assistantMessageId: start.assistantMessageId,
            conversationId: start.conversationId,
            lastSequence: 0,
            prompt: normalizedPrompt,
            subAgents: [],
            tools: [],
            userMessageId: start.userMessageId,
          };
          savePendingGeneration(pendingGenerationRef.current);
          setConversationId(start.conversationId);
          setConversationStatus("ACTIVE");
          setMessages((current) =>
            current.map((message) => {
              if (message.id === userMessageId) return { ...message, id: start.userMessageId };
              if (message.id === assistantId) {
                return { ...message, id: start.assistantMessageId };
              }
              return message;
            }),
          );
          void refreshRecentConversations();
        },
        onCancelled: () => {
          cancelled = true;
        },
        onSubAgent: (subAgent) => {
          const pending = pendingGenerationRef.current;
          if (pending) {
            pending.subAgents = mergeSubAgentEvents(pending.subAgents, subAgent);
            pending.lastSequence = subAgent.sequence;
            savePendingGeneration(pending);
          }
          setMessages((current) =>
            current.map((message) =>
              message.id === activeAssistantId
                ? {
                    ...message,
                    subAgents: mergeSubAgentEvents(message.subAgents, subAgent),
                  }
                : message,
            ),
          );
        },
        onTool: (tool) => {
          const pending = pendingGenerationRef.current;
          if (pending) {
            pending.tools = mergeToolEvents(pending.tools, tool);
            pending.lastSequence = tool.sequence;
            savePendingGeneration(pending);
          }
          setMessages((current) =>
            current.map((message) =>
              message.id === activeAssistantId
                ? { ...message, tools: mergeToolEvents(message.tools, tool) }
                : message,
            ),
          );
        },
        query: normalizedPrompt,
        session,
        signal: controller.signal,
      });
      setMessages((current) =>
        current.map((message) =>
          message.id === activeAssistantId
            ? { ...message, status: cancelled ? "stopped" : "done" }
            : message,
        ),
      );
      clearPendingGeneration();
      pendingGenerationRef.current = undefined;
      setResumeAvailable(false);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setMessages((current) =>
          current.map((message) =>
            message.id === activeAssistantId ? { ...message, status: "stopped" } : message,
          ),
        );
      } else {
        const message =
          error instanceof ChatStreamError ? error.message : "生成失败，请稍后重试";
        setStreamError(message);
        setMessages((current) =>
          current.map((item) =>
            item.id === activeAssistantId ? { ...item, status: "error" } : item,
          ),
        );

        if (error instanceof ChatStreamError && error.status === 401) {
          logout();
          router.replace("/login");
        }
        if (error instanceof ChatStreamError && error.code === "AI_MODEL_ERROR") {
          clearPendingGeneration();
          pendingGenerationRef.current = undefined;
        } else if (pendingGenerationRef.current) {
          setResumeAvailable(true);
        }
      }
    } finally {
      if (abortControllerRef.current === controller) abortControllerRef.current = null;
      setStreaming(false);
    }
  };

  const answerClarification = (
    clarification: ChatClarificationEvent,
    option: ChatClarificationEvent["options"][number],
  ) => {
    void sendPrompt(option.label, {
      clarificationId: clarification.clarificationId,
      optionCode: option.code,
    });
  };

  const retryLastPrompt = () => {
    if (pendingGenerationRef.current) {
      window.location.reload();
      return;
    }
    const lastUserMessage = messages.findLast((message) => message.role === "user");
    if (!lastUserMessage) return;
    setMessages((current) =>
      current.filter((message) => message.status !== "error"),
    );
    void sendPrompt(lastUserMessage.content);
  };

  const selectedApplication = applications.find(
    (application) => application.code === selectedApplicationCode,
  );
  const applicationMenuItems = applications.map((application) => ({
    key: application.code,
    label: (
      <span className={styles.applicationOption}>
        <strong>{application.name}</strong>
        <small>{application.description || application.code}</small>
      </span>
    ),
  }));

  const renderConversation = (conversation: Conversation, showStatus = false) => {
    const selected = conversation.id === conversationId;
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
                  if (event.key === "Escape") setRenamingId(undefined);
                }}
                value={renamingTitle}
              />
              <small>{formatConversationTime(conversation.lastMessageAt || conversation.updatedAt)}</small>
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
                {showStatus && conversation.status === "ARCHIVED" ? "已归档 · " : ""}
                {formatConversationTime(conversation.lastMessageAt || conversation.updatedAt)}
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
              if (key === "archive") void archiveSelectedConversation(conversation);
              if (key === "delete") void deleteSelectedConversation(conversation);
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

  return (
    <main className={styles.shell} data-theme={appearance}>
      <aside className={`${styles.sidebar} ${sidebarOpen ? styles.sidebarOpen : ""}`}>
        <div className={styles.brandRow}>
          <div className={styles.brandMark}>
            <Sparkles size={18} strokeWidth={2.4} />
          </div>
          <div className={styles.brandText}>
            <strong>灵析</strong>
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

        <div className={`${styles.historyHeader} ${styles.recentHistoryHeader}`}>
          <span>最近对话</span>
          <ActionIcon icon={Search} onClick={openHistory} size="small" title="搜索对话" />
        </div>
        <div className={styles.conversationList}>
          {conversationLoading ? (
            <div className={styles.conversationState}><Spin size="small" /></div>
          ) : recentConversations.length > 0 ? (
            recentConversations.map((conversation) => renderConversation(conversation))
          ) : (
            <span className={styles.conversationEmpty}>暂无会话记录</span>
          )}
        </div>

        <div className={styles.sidebarFooter}>
          <button className={styles.navItem} onClick={openHistory} type="button">
            <History size={17} />
            全部历史
          </button>
          <button className={styles.navItem} type="button">
            <Settings2 size={17} />
            设置
          </button>
          <div className={styles.profile}>
            <Avatar
              avatar={session.user.avatarUrl || session.user.displayName.slice(0, 1)}
              background={appearance === "dark" ? "#25324a" : "#e8f1ff"}
              size={34}
            />
            <span>
              <strong>{session.user.displayName}</strong>
              <small>个人空间</small>
            </span>
            <ActionIcon icon={LogOut} onClick={handleLogout} size="small" title="退出登录" />
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

      <Drawer
        className={styles.historyDrawer}
        destroyOnHidden
        onClose={() => setHistoryOpen(false)}
        open={historyOpen}
        placement="left"
        title="全部会话"
        width={380}
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
            <div className={styles.historyLoading}><Spin /></div>
          ) : historyConversations.length > 0 ? (
            historyConversations.map((conversation) => renderConversation(conversation, true))
          ) : (
            <Empty description="暂无会话记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </div>
        {historyConversations.length < historyTotal && (
          <Button
            block
            loading={historyLoading}
            onClick={() =>
              void loadHistoryConversations(historyCurrent + 1, historyKeyword, true)
            }
          >
            加载更多
          </Button>
        )}
      </Drawer>

      <section className={styles.workspace}>
        <header className={styles.header}>
          <ActionIcon
            className={styles.mobileMenu}
            icon={Menu}
            onClick={() => setSidebarOpen(true)}
            title="打开导航"
          />
          <Dropdown
            disabled={applicationLoading || applications.length === 0 || streaming}
            menu={{
              items: applicationMenuItems,
              onClick: ({ key }) => setSelectedApplicationCode(key),
              selectedKeys: selectedApplicationCode ? [selectedApplicationCode] : [],
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
                    (applicationLoading ? "正在加载应用…" : "暂无可用应用")}
                </strong>
                <small>
                  {selectedApplication?.description ||
                    selectedApplication?.code ||
                    (applicationLoading ? "请稍候" : "请先在管理端发布应用")}
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
              aria-label={appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"}
              icon={appearance === "dark" ? Sun : Moon}
              onClick={toggleAppearance}
              size="small"
              title={appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"}
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
              <Sparkles size={27} strokeWidth={1.9} />
            </div>
            <p className={styles.eyebrow}>你的企业智能协作伙伴</p>
            <h1>你好，今天想一起完成什么？</h1>
            <p className={styles.subtitle}>
              连接知识、数据与工具，把复杂工作变成清晰可靠的结果。
            </p>

            <div className={styles.suggestionGrid}>
              {suggestions.map(({ description, icon: SuggestionIcon, title }) => (
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
              ))}
            </div>
            </section>
          ) : (
            <section className={styles.messageStage} aria-label="对话内容">
              <div className={styles.messageList}>
                {messageLoading && messages.length === 0 && (
                  <div className={styles.messageLoading}><Spin /></div>
                )}
                {messages.length < messageTotal && (
                  <button
                    className={styles.loadOlder}
                    disabled={messageLoading}
                    onClick={() => void loadOlderMessages()}
                    type="button"
                  >
                    {messageLoading ? "正在加载…" : "加载更早消息"}
                  </button>
                )}
                {messages.map((message) => (
                  <article
                    className={`${styles.messageRow} ${
                      message.role === "user" ? styles.userMessage : styles.assistantMessage
                    }`}
                    key={message.id}
                  >
                    {message.role === "assistant" && (
                      <span className={styles.assistantAvatar} aria-hidden="true">
                        <Sparkles size={17} />
                      </span>
                    )}
                    <div className={styles.messageBody}>
                      <span className={styles.messageAuthor}>
                        {message.role === "user" ? "你" : "灵析通用助手"}
                      </span>
                      {message.role === "assistant" && message.subAgents?.length ? (
                        <SubAgentExecutionList subAgents={message.subAgents} />
                      ) : null}
                      {message.role === "assistant" && message.tools?.length ? (
                        <ToolExecutionList tools={message.tools} />
                      ) : null}
                      <div
                        aria-live={message.role === "assistant" ? "polite" : undefined}
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
                              {normalizeMarkdownTables(message.content)}
                            </Markdown>
                          ) : (
                            message.content
                          )
                        ) : message.status === "streaming" ? (
                            <span className={styles.thinking} aria-label="正在生成回答">
                              <i />
                              <i />
                              <i />
                            </span>
                          ) : (
                            "暂未生成内容"
                          )}
                        {message.status === "streaming" && message.content && (
                          <span className={styles.streamCaret} aria-hidden="true" />
                        )}
                      </div>
                      {message.status === "stopped" && (
                        <span className={styles.messageStatus}>已停止生成</span>
                      )}
                      {message.status === "error" && (
                        <div className={styles.messageError} role="alert">
                          <span>{message.errorMessage || streamError || "生成失败，请稍后重试"}</span>
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

          <div className={styles.composerWrap}>
            {pendingClarification && (
              <div
                className={styles.clarificationCard}
                data-testid="intent-clarification"
                role="group"
                aria-label="意图澄清"
              >
                <div>
                  <strong>需要你确认一下</strong>
                  <span>第 {pendingClarification.round} 轮</span>
                </div>
                <p>{pendingClarification.question}</p>
                {pendingClarification.options.length > 0 && (
                  <div className={styles.clarificationOptions}>
                    {pendingClarification.options.map((option) => (
                      <button
                        key={option.code}
                        disabled={streaming}
                        onClick={() => answerClarification(pendingClarification, option)}
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
              <div className={styles.archivedNotice}>该会话已归档，仅支持查看历史消息</div>
            )}
            {streamError && !messages.some((message) => message.status === "error") && (
              <button
                className={styles.requestError}
                onClick={() => setStreamError(undefined)}
                title="关闭提示"
                type="button"
              >
                {streamError}
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
                    : "向灵析提问，或描述你想完成的工作…"
                }
                rows={3}
                value={input}
              />
              <div className={styles.composerToolbar}>
                <div>
                  <ActionIcon icon={Paperclip} size="small" title="添加附件" />
                  <ActionIcon icon={Boxes} size="small" title="选择知识库" />
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
            <p className={styles.disclaimer}>AI 生成内容仅供参考，重要信息请核实。</p>
          </div>
        </div>
      </section>
    </main>
  );
}
