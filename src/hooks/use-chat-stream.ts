"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { AuthSession } from "@/lib/auth";
import type {
  ChatClarificationEvent,
  ChatExecutionTimelineEvent,
  ChatStart,
  ChatStepOutputEvent,
  ChatSubAgentEvent,
  ChatToolEvent,
  ClarificationResponse,
} from "@/lib/chat";
import {
  cancelChat,
  ChatStreamError,
  createConversation,
  isServerMessageId,
  resumeChat,
  streamChat,
} from "@/lib/chat";
import { normalizeMarkdownTables } from "@/lib/markdown";

export type ChatMessage = {
  animateOnMount?: boolean;
  content: string;
  errorMessage?: string;
  id: string;
  role: "assistant" | "user";
  status?: "done" | "error" | "stopped" | "streaming";
  subAgents?: ChatSubAgentEvent[];
  stepOutputs?: ChatStepOutputEvent[];
  timeline?: ChatExecutionTimelineEvent[];
  tools?: ChatToolEvent[];
};

type PendingGeneration = {
  answer: string;
  assistantMessageId: string;
  conversationId: string;
  lastSequence: number;
  prompt: string;
  clarification?: ChatClarificationEvent;
  cancelled?: boolean;
  subAgents: ChatSubAgentEvent[];
  stepOutputs: ChatStepOutputEvent[];
  timeline: ChatExecutionTimelineEvent[];
  tools: ChatToolEvent[];
  userMessageId: string;
};

type GenerationHandlers = {
  onCancelled: () => void;
  onChunk: (content: string, sequence: number) => void;
  onClarification: (event: ChatClarificationEvent) => void;
  onSnapshot: (content: string) => void;
  onStart: (start: ChatStart) => void;
  onStepOutput: (event: ChatStepOutputEvent) => void;
  onSubAgent: (event: ChatSubAgentEvent) => void;
  onTimeline: (event: ChatExecutionTimelineEvent) => void;
  onTool: (event: ChatToolEvent) => void;
};

type UseChatStreamOptions = {
  session: AuthSession | null;
  selectedApplicationCode: string | null;
  conversationId: string | null;
  onConversationCreated?: (id: string) => void;
  onLogout: () => void;
};

type UseChatStreamReturn = {
  answerClarification: (
    clarification: ChatClarificationEvent,
    option: { code: string; label: string },
  ) => void;
  clearError: () => void;
  discardPendingGeneration: () => void;
  messages: ChatMessage[];
  pendingClarification: ChatClarificationEvent | undefined;
  resumeAvailable: boolean;
  retryLastPrompt: () => void;
  sendPrompt: (text: string) => Promise<boolean>;
  setMessages: React.Dispatch<React.SetStateAction<ChatMessage[]>>;
  stopStreaming: () => Promise<void>;
  streamError: string | undefined;
  streaming: boolean;
};

const PENDING_GENERATION_KEY = "lingxi-pending-generation";
const MAX_STEP_OUTPUT_CHARACTERS = 12000;
const MODEL_ERROR_CODE = "AI_MODEL_ERROR";
const NON_RESUMABLE_ERROR_CODES = new Set([
  MODEL_ERROR_CODE,
  "AI_MODEL_TIMEOUT",
  "AGENT_INPUT_TOKEN_LIMIT",
  "AGENT_MODEL_CALL_LIMIT",
  "AGENT_OUTPUT_TOKEN_LIMIT",
  "AGENT_TOOL_CALL_LIMIT",
]);

function readPendingGeneration(): PendingGeneration | undefined {
  try {
    const value = sessionStorage.getItem(PENDING_GENERATION_KEY);
    if (!value) return undefined;
    const pending = JSON.parse(value) as PendingGeneration;
    if (!isServerMessageId(pending.assistantMessageId)) {
      clearPendingGeneration();
      return undefined;
    }
    return {
      ...pending,
      subAgents: pending.subAgents ?? [],
      stepOutputs: pending.stepOutputs ?? [],
      timeline: pending.timeline ?? [],
      tools: pending.tools ?? [],
    };
  } catch {
    sessionStorage.removeItem(PENDING_GENERATION_KEY);
    return undefined;
  }
}

function savePendingGeneration(pending: PendingGeneration) {
  sessionStorage.setItem(PENDING_GENERATION_KEY, JSON.stringify(pending));
}

function clearPendingGeneration() {
  sessionStorage.removeItem(PENDING_GENERATION_KEY);
}

function mergeByKey<T>(current: T[] | undefined, next: T, key: (item: T) => string | number): T[] {
  const events = current ?? [];
  if (!events.some((event) => key(event) === key(next))) return [...events, next];
  return events.map((event) => (key(event) === key(next) ? next : event));
}

function mergeTimelineEvents(current: ChatExecutionTimelineEvent[] | undefined, next: ChatExecutionTimelineEvent) {
  return mergeByKey(current, next, (event) => event.sequence).toSorted(
    (left, right) => left.sequence - right.sequence,
  );
}

function mergeStepOutputEvents(
  current: ChatStepOutputEvent[] | undefined,
  next: ChatStepOutputEvent,
): ChatStepOutputEvent[] {
  const events = current ?? [];
  const index = events.findIndex(
    (event) =>
      event.runId === next.runId
      && event.planNo === next.planNo
      && event.stepId === next.stepId,
  );
  if (index < 0) return [...events, next];
  const merged = `${events[index].content}${next.content}`;
  const content = merged.length > MAX_STEP_OUTPUT_CHARACTERS
    ? `…${merged.slice(-MAX_STEP_OUTPUT_CHARACTERS)}`
    : merged;
  return events.map((event, eventIndex) =>
    eventIndex === index ? { ...next, content } : event,
  );
}

function generateMessageId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function useChatStream({
  session,
  selectedApplicationCode,
  conversationId: externalConversationId,
  onConversationCreated,
  onLogout,
}: UseChatStreamOptions): UseChatStreamReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [streamError, setStreamError] = useState<string>();
  const [pendingClarification, setPendingClarification] = useState<ChatClarificationEvent>();
  const [resumeAvailable, setResumeAvailable] = useState(false);

  const abortControllerRef = useRef<AbortController | null>(null);
  const pendingGenerationRef = useRef<PendingGeneration | undefined>(undefined);
  const submittingRef = useRef(false);
  const conversationIdRef = useRef(externalConversationId);

  useEffect(() => {
    conversationIdRef.current = externalConversationId;
  }, [externalConversationId]);

  const updateAssistant = useCallback(
    (pending: PendingGeneration, update: (message: ChatMessage) => ChatMessage) => {
      setMessages((current) =>
        current.map((message) =>
          message.id === pending.assistantMessageId ? update(message) : message,
        ),
      );
    },
    [],
  );

  const finishGeneration = useCallback((pending: PendingGeneration) => {
    if (pending.cancelled) return;
    setMessages((current) =>
      current.map((message) =>
        message.id === pending.assistantMessageId ? { ...message, status: "done" } : message,
      ),
    );
    setResumeAvailable(false);
    if (pending.clarification) {
      savePendingGeneration(pending);
      pendingGenerationRef.current = pending;
      return;
    }
    clearPendingGeneration();
    pendingGenerationRef.current = undefined;
  }, []);

  const createHandlers = useCallback(
    (pending: PendingGeneration, isActive: () => boolean = () => true): GenerationHandlers => ({
      onCancelled: () => {
        if (!isActive()) return;
        pending.cancelled = true;
        updateAssistant(pending, (message) => ({ ...message, status: "stopped" }));
        clearPendingGeneration();
        pendingGenerationRef.current = undefined;
        setPendingClarification(undefined);
        setResumeAvailable(false);
      },
      onChunk: (content, sequence) => {
        if (!isActive()) return;
        pending.answer += content;
        pending.lastSequence = Math.max(pending.lastSequence, sequence);
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({
          ...message,
          content: normalizeMarkdownTables(pending.answer),
          status: "streaming",
        }));
      },
      onClarification: (event) => {
        if (!isActive()) return;
        pending.clarification = event;
        pending.lastSequence = Math.max(pending.lastSequence, event.sequence);
        savePendingGeneration(pending);
        setPendingClarification(event);
      },
      onSnapshot: (content) => {
        if (!isActive()) return;
        pending.answer = content;
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({
          ...message,
          content: normalizeMarkdownTables(content),
          status: "streaming",
        }));
      },
      onStart: (start) => {
        if (!isActive()) return;
        const temporaryAssistantId = pending.assistantMessageId;
        const temporaryUserId = pending.userMessageId;
        pending.assistantMessageId = start.assistantMessageId;
        pending.conversationId = start.conversationId;
        pending.userMessageId = start.userMessageId;
        conversationIdRef.current = start.conversationId;
        savePendingGeneration(pending);
        setMessages((current) =>
          current.map((message) => {
            if (message.id === temporaryAssistantId) return { ...message, id: start.assistantMessageId };
            if (message.id === temporaryUserId) return { ...message, id: start.userMessageId };
            return message;
          }),
        );
        onConversationCreated?.(start.conversationId);
      },
      onSubAgent: (event) => {
        if (!isActive()) return;
        pending.subAgents = mergeByKey(pending.subAgents, event, (item) => item.delegationId);
        pending.lastSequence = Math.max(pending.lastSequence, event.sequence);
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({ ...message, subAgents: pending.subAgents }));
      },
      onStepOutput: (event) => {
        if (!isActive()) return;
        pending.stepOutputs = mergeStepOutputEvents(pending.stepOutputs, event);
        pending.lastSequence = Math.max(pending.lastSequence, event.sequence);
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({
          ...message,
          stepOutputs: pending.stepOutputs,
        }));
      },
      onTimeline: (event) => {
        if (!isActive()) return;
        pending.timeline = mergeTimelineEvents(pending.timeline, event);
        pending.lastSequence = Math.max(pending.lastSequence, event.sequence);
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({ ...message, timeline: pending.timeline }));
      },
      onTool: (event) => {
        if (!isActive()) return;
        pending.tools = mergeByKey(pending.tools, event, (item) => item.invocationId);
        pending.lastSequence = Math.max(pending.lastSequence, event.sequence);
        savePendingGeneration(pending);
        updateAssistant(pending, (message) => ({ ...message, tools: pending.tools }));
      },
    }),
    [onConversationCreated, updateAssistant],
  );

  const handleGenerationError = useCallback(
    (pending: PendingGeneration, error: unknown, fallbackMessage: string) => {
      if (error instanceof DOMException && error.name === "AbortError") return;
      const message = error instanceof ChatStreamError ? error.message : fallbackMessage;
      setStreamError(message);
      updateAssistant(pending, (current) => ({ ...current, errorMessage: message, status: "error" }));
      if (error instanceof ChatStreamError && error.status === 401) {
        clearPendingGeneration();
        pendingGenerationRef.current = undefined;
        onLogout();
        return;
      }
      if (!isServerMessageId(pending.assistantMessageId)) {
        clearPendingGeneration();
        setResumeAvailable(false);
        return;
      }
      const resumable = !(error instanceof ChatStreamError)
        || (!NON_RESUMABLE_ERROR_CODES.has(error.code ?? "") && error.status !== 404);
      setResumeAvailable(resumable);
    },
    [onLogout, updateAssistant],
  );

  const resumeGeneration = useCallback(
    (pending: PendingGeneration, isActive: () => boolean = () => true) => {
      if (!session) return;
      const controller = new AbortController();
      abortControllerRef.current = controller;
      setStreaming(true);
      setStreamError(undefined);
      const handlers = createHandlers(pending, isActive);
      void resumeChat({
        afterSequence: pending.lastSequence,
        assistantMessageId: pending.assistantMessageId,
        ...handlers,
        session,
        signal: controller.signal,
      })
        .then(() => {
          if (isActive()) finishGeneration(pending);
        })
        .catch((error: unknown) => {
          if (isActive()) handleGenerationError(pending, error, "恢复生成任务失败，请稍后刷新");
        })
        .finally(() => {
          if (abortControllerRef.current === controller) abortControllerRef.current = null;
          if (isActive()) setStreaming(false);
        });
    },
    [createHandlers, finishGeneration, handleGenerationError, session],
  );

  useEffect(() => {
    if (!session || pendingGenerationRef.current) return;
    const pending = readPendingGeneration();
    if (!pending) return;
    pendingGenerationRef.current = pending;
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setMessages((current) => {
        if (current.some((message) => message.id === pending.assistantMessageId)) return current;
        return [
          ...current,
          { content: pending.prompt, id: pending.userMessageId, role: "user", status: "done" },
          {
            content: normalizeMarkdownTables(pending.answer),
            id: pending.assistantMessageId,
            role: "assistant",
            status: "streaming",
            subAgents: pending.subAgents,
            stepOutputs: pending.stepOutputs,
            timeline: pending.timeline,
            tools: pending.tools,
          },
        ];
      });
      onConversationCreated?.(pending.conversationId);
      resumeGeneration(pending, () => active);
    });
    return () => {
      active = false;
      abortControllerRef.current?.abort();
    };
  }, [onConversationCreated, resumeGeneration, session]);

  const startGeneration = useCallback(
    async (pending: PendingGeneration, clarificationResponse?: ClarificationResponse) => {
      if (!session || !selectedApplicationCode) return;
      pendingGenerationRef.current = pending;
      setStreaming(true);
      setStreamError(undefined);
      setResumeAvailable(false);
      const controller = new AbortController();
      abortControllerRef.current = controller;
      const handlers = createHandlers(pending);
      try {
        await streamChat({
          applicationCode: selectedApplicationCode,
          clarificationResponse,
          clientMessageId: pending.userMessageId,
          conversationId: pending.conversationId,
          ...handlers,
          query: pending.prompt,
          session,
          signal: controller.signal,
        });
        finishGeneration(pending);
      } catch (error) {
        handleGenerationError(pending, error, "生成失败，请稍后重试");
      } finally {
        if (abortControllerRef.current === controller) abortControllerRef.current = null;
        setStreaming(false);
      }
    },
    [createHandlers, finishGeneration, handleGenerationError, selectedApplicationCode, session],
  );

  const sendPrompt = useCallback(
    async (text: string) => {
      if (!session || !selectedApplicationCode || streaming || submittingRef.current || !text.trim()) return false;
      if (pendingGenerationRef.current) {
        setStreamError(pendingGenerationRef.current.clarification
          ? "请先完成当前确认，再提交新问题"
          : "上一次生成尚未恢复，请重试或新建对话");
        return false;
      }
      submittingRef.current = true;
      try {
        let conversationId = conversationIdRef.current;
        if (!conversationId) {
          const conversation = await createConversation(session);
          conversationId = conversation.id;
          conversationIdRef.current = conversation.id;
          onConversationCreated?.(conversation.id);
        }
        const prompt = text.trim();
        const pending: PendingGeneration = {
          answer: "",
          assistantMessageId: generateMessageId(),
          conversationId,
          lastSequence: 0,
          prompt,
          subAgents: [],
          stepOutputs: [],
          timeline: [],
          tools: [],
          userMessageId: generateMessageId(),
        };
        setPendingClarification(undefined);
        setMessages((current) => [
          ...current,
          {
            animateOnMount: true,
            content: prompt,
            id: pending.userMessageId,
            role: "user",
            status: "done",
          },
          { content: "", id: pending.assistantMessageId, role: "assistant", status: "streaming" },
        ]);
        void startGeneration(pending);
        return true;
      } catch (error) {
        setStreamError(error instanceof ChatStreamError ? error.message : "创建会话失败");
        return false;
      } finally {
        submittingRef.current = false;
      }
    },
    [onConversationCreated, selectedApplicationCode, session, startGeneration, streaming],
  );

  /** 用户显式切换对话上下文时，丢弃仅用于当前页面续传的本地状态。 */
  const discardPendingGeneration = useCallback(() => {
    clearPendingGeneration();
    pendingGenerationRef.current = undefined;
    setPendingClarification(undefined);
    setResumeAvailable(false);
  }, []);

  const stopStreaming = useCallback(async () => {
    const pending = pendingGenerationRef.current;
    if (!pending || !session || !streaming) return;
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    if (!isServerMessageId(pending.assistantMessageId)) {
      updateAssistant(pending, (message) => ({ ...message, status: "stopped" }));
      clearPendingGeneration();
      pendingGenerationRef.current = undefined;
      setPendingClarification(undefined);
      setResumeAvailable(false);
      setStreaming(false);
      return;
    }
    try {
      await cancelChat(pending.assistantMessageId, session);
      updateAssistant(pending, (message) => ({ ...message, status: "stopped" }));
      clearPendingGeneration();
      pendingGenerationRef.current = undefined;
      setPendingClarification(undefined);
      setResumeAvailable(false);
    } catch (error) {
      setStreamError(error instanceof ChatStreamError ? error.message : "停止生成失败，请稍后重试");
      setResumeAvailable(true);
    } finally {
      setStreaming(false);
    }
  }, [session, streaming, updateAssistant]);

  const retryLastPrompt = useCallback(() => {
    const pending = pendingGenerationRef.current;
    if (!pending || streaming) return;
    if (resumeAvailable) {
      resumeGeneration(pending);
      return;
    }
    const prompt = pending.prompt;
    clearPendingGeneration();
    pendingGenerationRef.current = undefined;
    setMessages((current) => current.filter((message) => message.id !== pending.assistantMessageId));
    void sendPrompt(prompt);
  }, [resumeAvailable, resumeGeneration, sendPrompt, streaming]);

  const answerClarification = useCallback(
    (clarification: ChatClarificationEvent, option: { code: string; label: string }) => {
      const previous = pendingGenerationRef.current;
      if (!previous || !session || !selectedApplicationCode || streaming) return;
      const pending: PendingGeneration = {
        answer: "",
        assistantMessageId: generateMessageId(),
        conversationId: previous.conversationId,
        lastSequence: 0,
        prompt: option.label,
        subAgents: [],
        stepOutputs: [],
        timeline: [],
        tools: [],
        userMessageId: generateMessageId(),
      };
      setPendingClarification(undefined);
      setMessages((current) => [
        ...current,
        { content: option.label, id: pending.userMessageId, role: "user", status: "done" },
        { content: "", id: pending.assistantMessageId, role: "assistant", status: "streaming" },
      ]);
      void startGeneration(pending, {
        clarificationId: clarification.clarificationId,
        interactionType: clarification.interactionType,
        optionCode: option.code,
      });
    },
    [selectedApplicationCode, session, startGeneration, streaming],
  );

  const clearError = useCallback(() => setStreamError(undefined), []);

  return {
    answerClarification,
    clearError,
    discardPendingGeneration,
    messages,
    pendingClarification,
    resumeAvailable,
    retryLastPrompt,
    sendPrompt,
    setMessages,
    stopStreaming,
    streamError,
    streaming,
  };
}
