import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useChatStream } from "@/hooks/use-chat-stream";
import WorkbenchShell from "../workbench-shell";

const conversation = vi.hoisted(() => ({
  recentConversations: [],
  historyConversations: [],
  setConversationId: vi.fn(),
  setConversationStatus: vi.fn(),
  refreshRecentConversations: vi.fn(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@lobehub/ui/chat", () => ({ ChatInputArea: () => null }));
vi.mock("@lobehub/ui", () => ({
  ActionIcon: () => null,
  Avatar: () => null,
  Button: () => null,
  Markdown: () => null,
  Tag: () => null,
}));
vi.mock("../auth-provider", () => ({
  useAuth: () => ({ ready: false, session: null, logout: vi.fn() }),
}));
vi.mock("../app-provider", () => ({
  useThemeMode: () => ({ appearance: "light", toggleAppearance: vi.fn() }),
}));
vi.mock("@/hooks/use-conversation", () => ({ useConversation: () => conversation }));
vi.mock("@/hooks/use-pinned-conversations", () => ({
  usePinnedConversations: () => ({ pinnedConversations: [], isPinned: () => false }),
}));
vi.mock("@/hooks/use-chat-stream", () => ({ useChatStream: vi.fn(() => ({})) }));

function startConversation(id: string) {
  renderToStaticMarkup(createElement(WorkbenchShell));
  const options = vi.mocked(useChatStream).mock.calls.at(-1)![0];
  options.onConversationCreated!(id);
}

describe("WorkbenchShell conversation synchronization", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    conversation.refreshRecentConversations.mockResolvedValue(undefined);
  });

  it("首次发送取得服务端会话 ID 后立即刷新侧边栏，无需等待生成完成", () => {
    startConversation("123");

    expect(conversation.setConversationId).toHaveBeenCalledWith("123");
    expect(conversation.setConversationStatus).toHaveBeenCalledWith("ACTIVE");
    expect(conversation.refreshRecentConversations).toHaveBeenCalledOnce();
  });

  it("历史列表请求未完成时仍立即切换到服务端确认的会话", () => {
    conversation.refreshRecentConversations.mockReturnValue(new Promise(() => {}));

    startConversation("456");

    expect(conversation.setConversationId).toHaveBeenCalledWith("456");
    expect(conversation.setConversationStatus).toHaveBeenCalledWith("ACTIVE");
    expect(conversation.refreshRecentConversations).toHaveBeenCalledOnce();
  });
});
