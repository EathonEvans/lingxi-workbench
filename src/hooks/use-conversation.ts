"use client";

import { useState, useCallback } from "react";
import type { AuthSession } from "@/lib/auth";
import type { Conversation, ConversationMessage } from "@/lib/chat";
import {
  archiveConversation,
  deleteConversation,
  listConversationMessages,
  listConversations,
  renameConversation,
} from "@/lib/chat";

type ChatMessage = {
  content: string;
  errorMessage?: string;
  id: string;
  role: "assistant" | "user";
  status?: "done" | "error" | "stopped" | "streaming";
};

type UseConversationOptions = {
  session: AuthSession | null;
  onRequestError: (error: unknown, fallback: string) => void;
};

type UseConversationReturn = {
  conversationId: string | null;
  conversationStatus: Conversation["status"] | undefined;
  recentConversations: Conversation[];
  conversationLoading: boolean;
  historyOpen: boolean;
  historyConversations: Conversation[];
  historyLoading: boolean;
  historyCurrent: number;
  historyTotal: number;
  historyKeyword: string;
  messageLoading: boolean;
  messageCurrent: number;
  messageTotal: number;
  renamingId: string | undefined;
  renamingTitle: string;
  setConversationId: (id: string | null) => void;
  setConversationStatus: (status: Conversation["status"] | undefined) => void;
  setHistoryOpen: (open: boolean) => void;
  setHistoryKeyword: (keyword: string) => void;
  setRenamingTitle: (title: string) => void;
  refreshRecentConversations: () => Promise<void>;
  loadHistoryConversations: (
    current: number,
    keyword: string,
    append?: boolean,
  ) => Promise<void>;
  selectConversation: (conv: Conversation) => Promise<void>;
  loadOlderMessages: () => Promise<void>;
  beginRename: (conv: Conversation) => void;
  cancelRename: () => void;
  submitRename: (conv: Conversation) => Promise<string | undefined>;
  archiveSelectedConversation: (conv: Conversation) => Promise<boolean>;
  deleteSelectedConversation: (conv: Conversation) => Promise<boolean>;
};

function toChatMessage(msg: ConversationMessage): ChatMessage | undefined {
  const role = msg.role === "USER" ? "user" : "assistant";
  if (msg.role === ("SYSTEM" as const)) return undefined;

  let status: ChatMessage["status"] = "done";
  if (msg.status === "GENERATING") status = "streaming";
  else if (msg.status === "FAILED" || msg.status === "INTERRUPTED")
    status = "error";
  else if (msg.status === "CANCELLED") status = "stopped";

  return {
    content: msg.content,
    errorMessage: msg.errorMessage,
    id: msg.id,
    role,
    status,
  };
}

export function useConversation({
  session,
  onRequestError,
}: UseConversationOptions): UseConversationReturn {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [conversationStatus, setConversationStatus] =
    useState<Conversation["status"]>();
  const [recentConversations, setRecentConversations] = useState<
    Conversation[]
  >([]);
  const [conversationLoading, setConversationLoading] = useState(true);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyConversations, setHistoryConversations] = useState<
    Conversation[]
  >([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyCurrent, setHistoryCurrent] = useState(1);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyKeyword, setHistoryKeyword] = useState("");
  const [messageLoading, setMessageLoading] = useState(false);
  const [messageCurrent, setMessageCurrent] = useState(1);
  const [messageTotal, setMessageTotal] = useState(0);
  const [renamingId, setRenamingId] = useState<string>();
  const [renamingTitle, setRenamingTitle] = useState("");

  const requireSession = useCallback((): AuthSession => {
    if (!session) throw new Error("未登录");
    return session;
  }, [session]);

  const refreshRecentConversations = useCallback(async () => {
    try {
      const s = requireSession();
      const result = await listConversations(s, {
        current: 1,
        size: 20,
        status: "ACTIVE",
      });
      setRecentConversations(result.records);
      setConversationLoading(false);
    } catch (error) {
      onRequestError(error, "刷新最近会话失败");
      setConversationLoading(false);
    }
  }, [requireSession, onRequestError]);

  const loadHistoryConversations = useCallback(
    async (current: number, keyword: string, append = false) => {
      setHistoryLoading(true);
      try {
        const s = requireSession();
        const result = await listConversations(s, {
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
        onRequestError(error, "查询全部历史失败");
      } finally {
        setHistoryLoading(false);
      }
    },
    [requireSession, onRequestError],
  );

  const selectConversation = useCallback(
    async (conversation: Conversation) => {
      const s = requireSession();
      setHistoryOpen(false);
      setConversationId(conversation.id);
      setConversationStatus(conversation.status);
      setMessageLoading(true);
      try {
        const result = await listConversationMessages(conversation.id, s, {
          current: 1,
          size: 50,
        });
        // message loading now handled by caller via setMessages
        setMessageCurrent(result.current);
        setMessageTotal(result.total);
      } catch (error) {
        onRequestError(error, "加载历史消息失败");
      } finally {
        setMessageLoading(false);
      }
    },
    [requireSession, onRequestError],
  );

  const loadOlderMessages = useCallback(async () => {
    if (!conversationId || messageLoading) return;
    const s = requireSession();
    setMessageLoading(true);
    try {
      const result = await listConversationMessages(conversationId, s, {
        current: messageCurrent + 1,
        size: 50,
      });
      // caller appends older messages
      setMessageCurrent(result.current);
      setMessageTotal(result.total);
    } catch (error) {
      onRequestError(error, "加载更早消息失败");
    } finally {
      setMessageLoading(false);
    }
  }, [
    conversationId,
    messageLoading,
    messageCurrent,
    requireSession,
    onRequestError,
  ]);

  const beginRename = useCallback(
    (conversation: Conversation) => {
      setRenamingId(conversation.id);
      setRenamingTitle(conversation.title);
    },
    [],
  );

  const cancelRename = useCallback(() => {
    setRenamingId(undefined);
    setRenamingTitle("");
  }, []);

  const submitRename = useCallback(
    async (conversation: Conversation) => {
      const title = renamingTitle.trim();
      setRenamingId(undefined);
      if (!title || title === conversation.title) return undefined;
      try {
        const s = requireSession();
        await renameConversation(conversation.id, title, s);
        setRecentConversations((items) =>
          items.map((c) =>
            c.id === conversation.id ? { ...c, title } : c,
          ),
        );
        setHistoryConversations((items) =>
          items.map((c) =>
            c.id === conversation.id ? { ...c, title } : c,
          ),
        );
        return title;
      } catch (error) {
        onRequestError(error, "修改标题失败");
        return undefined;
      }
    },
    [renamingTitle, requireSession, onRequestError],
  );

  const archiveSelectedConversation = useCallback(
    async (conversation: Conversation) => {
      try {
        const s = requireSession();
        await archiveConversation(conversation.id, s);
        setRecentConversations((items) =>
          items.filter((c) => c.id !== conversation.id),
        );
        if (conversationId === conversation.id) {
          setConversationStatus("ARCHIVED");
          setConversationId(null);
        }
        return true;
      } catch (error) {
        onRequestError(error, "归档会话失败");
        return false;
      }
    },
    [conversationId, requireSession, onRequestError],
  );

  const deleteSelectedConversation = useCallback(
    async (conversation: Conversation) => {
      try {
        const s = requireSession();
        await deleteConversation(conversation.id, s);
        setRecentConversations((items) =>
          items.filter((c) => c.id !== conversation.id),
        );
        setHistoryConversations((items) =>
          items.filter((c) => c.id !== conversation.id),
        );
        if (conversationId === conversation.id) setConversationId(null);
        return true;
      } catch (error) {
        onRequestError(error, "删除会话失败");
        return false;
      }
    },
    [conversationId, requireSession, onRequestError],
  );

  return {
    conversationId,
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
    setRenamingTitle,
    refreshRecentConversations,
    loadHistoryConversations,
    selectConversation,
    loadOlderMessages,
    beginRename,
    cancelRename,
    submitRename,
    archiveSelectedConversation,
    deleteSelectedConversation,
    toChatMessage: (msg: ConversationMessage) => toChatMessage(msg),
  } as UseConversationReturn & {
    toChatMessage: (msg: ConversationMessage) => ChatMessage | undefined;
  };
}
