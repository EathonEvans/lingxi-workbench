"use client";

import { useCallback, useEffect, useState } from "react";
import type { Conversation } from "@/lib/chat";

const MAX_PINNED_CONVERSATIONS = 20;
const STORAGE_KEY_PREFIX = "lingxi-workbench-pinned-conversations";

function storageKey(userId: number) {
  return `${STORAGE_KEY_PREFIX}:${userId}`;
}

function isConversation(value: unknown): value is Conversation {
  if (!value || typeof value !== "object") return false;
  const item = value as Partial<Conversation>;
  return (
    typeof item.id === "string" &&
    typeof item.title === "string" &&
    typeof item.createdAt === "string" &&
    typeof item.updatedAt === "string" &&
    typeof item.messageCount === "number" &&
    (item.status === "ACTIVE" || item.status === "ARCHIVED")
  );
}

function readPinnedConversations(userId: number): Conversation[] {
  try {
    const value = window.localStorage.getItem(storageKey(userId));
    if (!value) return [];
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed)
      ? parsed.filter(isConversation).slice(0, MAX_PINNED_CONVERSATIONS)
      : [];
  } catch {
    return [];
  }
}

function writePinnedConversations(userId: number, items: Conversation[]) {
  try {
    window.localStorage.setItem(storageKey(userId), JSON.stringify(items));
  } catch {
    // 浏览器禁用存储时，置顶状态仍在当前页面生命周期内生效
  }
}

export function usePinnedConversations(userId?: number) {
  const [pinnedConversations, setPinnedConversations] = useState<
    Conversation[]
  >([]);

  useEffect(() => {
    if (!userId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPinnedConversations(readPinnedConversations(userId));

    const handleStorage = (event: StorageEvent) => {
      if (event.key === storageKey(userId)) {
        setPinnedConversations(readPinnedConversations(userId));
      }
    };
    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  }, [userId]);

  const commit = useCallback(
    (update: (items: Conversation[]) => Conversation[]) => {
      setPinnedConversations((items) => {
        const nextItems = update(items).slice(0, MAX_PINNED_CONVERSATIONS);
        if (userId) writePinnedConversations(userId, nextItems);
        return nextItems;
      });
    },
    [userId],
  );

  const togglePinned = useCallback(
    (conversation: Conversation) => {
      commit((items) =>
        items.some((item) => item.id === conversation.id)
          ? items.filter((item) => item.id !== conversation.id)
          : [conversation, ...items],
      );
    },
    [commit],
  );

  const removePinned = useCallback(
    (conversationId: string) => {
      commit((items) => items.filter((item) => item.id !== conversationId));
    },
    [commit],
  );

  const updatePinned = useCallback(
    (conversation: Conversation) => {
      commit((items) =>
        items.map((item) =>
          item.id === conversation.id ? conversation : item,
        ),
      );
    },
    [commit],
  );

  const isPinned = useCallback(
    (conversationId: string) =>
      pinnedConversations.some((item) => item.id === conversationId),
    [pinnedConversations],
  );

  return {
    isPinned,
    pinnedConversations,
    removePinned,
    togglePinned,
    updatePinned,
  };
}
