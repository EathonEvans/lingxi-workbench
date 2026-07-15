"use client";

import { ActionIcon, Avatar, Button, Tag } from "@lobehub/ui";
import { ChatInputArea } from "@lobehub/ui/chat";
import {
  Bot,
  Boxes,
  CircleStop,
  ChevronsUpDown,
  FileSearch,
  FolderKanban,
  History,
  ListTodo,
  LogOut,
  Menu,
  MessageSquareText,
  Moon,
  MoreHorizontal,
  Paperclip,
  PenLine,
  Plus,
  RotateCcw,
  Search,
  Settings2,
  Sparkles,
  Sun,
  WandSparkles,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ChatStreamError, streamChat } from "@/lib/chat";
import { useAuth } from "./auth-provider";
import { useThemeMode } from "./app-provider";
import styles from "./workbench-shell.module.css";

const conversations = [
  { icon: FileSearch, title: "分析本季度经营数据", time: "刚刚" },
  { icon: PenLine, title: "优化产品需求文档", time: "昨天" },
  { icon: FolderKanban, title: "梳理项目实施计划", time: "周一" },
];

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
  id: string;
  role: "assistant" | "user";
  status?: "done" | "error" | "stopped" | "streaming";
};

function createMessageId(role: ChatMessage["role"]) {
  return `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function WorkbenchShell() {
  const router = useRouter();
  const { logout, ready, session } = useAuth();
  const { appearance, toggleAppearance } = useThemeMode();
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [streamError, setStreamError] = useState<string>();
  const [streaming, setStreaming] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const messageEndRef = useRef<HTMLDivElement | null>(null);

  const startTask = (prompt: string) => {
    setInput(prompt);
  };

  useEffect(() => {
    if (ready && !session) router.replace("/login");
  }, [ready, router, session]);

  useEffect(() => {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    messageEndRef.current?.scrollIntoView({
      behavior: reduceMotion || streaming ? "auto" : "smooth",
      block: "end",
    });
  }, [messages, streaming]);

  useEffect(() => () => abortControllerRef.current?.abort(), []);

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

  const handleLogout = () => {
    abortControllerRef.current?.abort();
    logout();
    router.replace("/login");
  };

  const startNewChat = () => {
    abortControllerRef.current?.abort();
    setInput("");
    setMessages([]);
    setStreamError(undefined);
    setStreaming(false);
  };

  const stopStreaming = () => abortControllerRef.current?.abort();

  const sendPrompt = async (prompt: string) => {
    const normalizedPrompt = prompt.trim();
    if (!normalizedPrompt || streaming || !session) return;

    const assistantId = createMessageId("assistant");
    const nextMessages: ChatMessage[] = [
      {
        content: normalizedPrompt,
        id: createMessageId("user"),
        role: "user",
        status: "done",
      },
      { content: "", id: assistantId, role: "assistant", status: "streaming" },
    ];

    setMessages((current) => [...current, ...nextMessages]);
    setInput("");
    setStreamError(undefined);
    setStreaming(true);

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      await streamChat({
        onChunk: (content) => {
          setMessages((current) =>
            current.map((message) =>
              message.id === assistantId
                ? { ...message, content: message.content + content }
                : message,
            ),
          );
        },
        prompt: normalizedPrompt,
        session,
        signal: controller.signal,
      });
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId ? { ...message, status: "done" } : message,
        ),
      );
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setMessages((current) =>
          current.map((message) =>
            message.id === assistantId ? { ...message, status: "stopped" } : message,
          ),
        );
      } else {
        const message =
          error instanceof ChatStreamError ? error.message : "生成失败，请稍后重试";
        setStreamError(message);
        setMessages((current) =>
          current.map((item) =>
            item.id === assistantId ? { ...item, status: "error" } : item,
          ),
        );

        if (error instanceof ChatStreamError && error.status === 401) {
          logout();
          router.replace("/login");
        }
      }
    } finally {
      if (abortControllerRef.current === controller) abortControllerRef.current = null;
      setStreaming(false);
    }
  };

  const retryLastPrompt = () => {
    const lastUserMessage = messages.findLast((message) => message.role === "user");
    if (!lastUserMessage) return;
    setMessages((current) =>
      current.filter((message) => message.status !== "error"),
    );
    void sendPrompt(lastUserMessage.content);
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

        <nav className={styles.primaryNav} aria-label="主导航">
          <button className={styles.navItemActive} type="button">
            <MessageSquareText size={17} />
            对话
          </button>
          <button className={styles.navItem} type="button">
            <Boxes size={17} />
            智能体
          </button>
          <button className={styles.navItem} type="button">
            <FolderKanban size={17} />
            知识库
          </button>
        </nav>

        <div className={styles.historyHeader}>
          <span>最近对话</span>
          <ActionIcon icon={Search} size="small" title="搜索对话" />
        </div>
        <div className={styles.conversationList}>
          {conversations.map(({ icon: ConversationIcon, title, time }) => (
            <button className={styles.conversation} key={title} type="button">
              <ConversationIcon size={16} />
              <span>
                <strong>{title}</strong>
                <small>{time}</small>
              </span>
              <MoreHorizontal className={styles.conversationMore} size={15} />
            </button>
          ))}
        </div>

        <div className={styles.sidebarFooter}>
          <button className={styles.navItem} type="button">
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

      <section className={styles.workspace}>
        <header className={styles.header}>
          <ActionIcon
            className={styles.mobileMenu}
            icon={Menu}
            onClick={() => setSidebarOpen(true)}
            title="打开导航"
          />
          <button className={styles.modelPicker} type="button">
            <span className={styles.modelIcon}>
              <Bot size={17} />
            </span>
            <span>
              <strong>灵析通用助手</strong>
              <small>智能选择模型</small>
            </span>
            <ChevronsUpDown size={14} />
          </button>
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
          {messages.length === 0 ? (
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
                      <div
                        aria-live={message.role === "assistant" ? "polite" : undefined}
                        className={styles.messageContent}
                      >
                        {message.content ||
                          (message.status === "streaming" ? (
                            <span className={styles.thinking} aria-label="正在生成回答">
                              <i />
                              <i />
                              <i />
                            </span>
                          ) : (
                            "暂未生成内容"
                          ))}
                        {message.status === "streaming" && message.content && (
                          <span className={styles.streamCaret} aria-hidden="true" />
                        )}
                      </div>
                      {message.status === "stopped" && (
                        <span className={styles.messageStatus}>已停止生成</span>
                      )}
                      {message.status === "error" && (
                        <div className={styles.messageError} role="alert">
                          <span>{streamError || "生成失败，请稍后重试"}</span>
                          <button onClick={retryLastPrompt} type="button">
                            <RotateCcw size={14} />
                            重新生成
                          </button>
                        </div>
                      )}
                    </div>
                  </article>
                ))}
                <div ref={messageEndRef} />
              </div>
            </section>
          )}

          <div className={styles.composerWrap}>
            <div className={styles.composer}>
              <ChatInputArea.Inner
                onChange={(event) => setInput(event.target.value)}
                onSend={() => void sendPrompt(input)}
                placeholder="向灵析提问，或描述你想完成的工作…"
                rows={3}
                value={input}
              />
              <div className={styles.composerToolbar}>
                <div>
                  <ActionIcon icon={Paperclip} size="small" title="添加附件" />
                  <ActionIcon icon={Boxes} size="small" title="选择知识库" />
                </div>
                {streaming ? (
                  <button className={styles.stopButton} onClick={stopStreaming} type="button">
                    <CircleStop size={14} />
                    停止生成
                  </button>
                ) : (
                  <span>Enter 发送 · Shift + Enter 换行</span>
                )}
              </div>
            </div>
            <p className={styles.disclaimer}>AI 生成内容仅供参考，重要信息请核实。</p>
          </div>
        </div>
      </section>
    </main>
  );
}
