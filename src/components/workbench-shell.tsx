"use client";

import { ActionIcon, Avatar, Button, Tag } from "@lobehub/ui";
import { ChatInputArea } from "@lobehub/ui/chat";
import {
  Bot,
  Boxes,
  ChevronsUpDown,
  Clock3,
  FileSearch,
  FolderKanban,
  History,
  Menu,
  MessageSquareText,
  MoreHorizontal,
  Paperclip,
  PenLine,
  Plus,
  Search,
  Settings2,
  Sparkles,
  WandSparkles,
} from "lucide-react";
import { useState } from "react";
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

export function WorkbenchShell() {
  const [input, setInput] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const startTask = (prompt: string) => {
    setInput(prompt);
  };

  return (
    <main className={styles.shell}>
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
          onClick={() => setInput("")}
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
          <button className={styles.profile} type="button">
            <Avatar avatar="林" background="#e8f1ff" size={34} />
            <span>
              <strong>林析</strong>
              <small>个人空间</small>
            </span>
            <ChevronsUpDown size={14} />
          </button>
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
            <ActionIcon icon={Clock3} title="任务记录" />
            <ActionIcon icon={MoreHorizontal} title="更多操作" />
          </div>
        </header>

        <div className={styles.content}>
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

          <div className={styles.composerWrap}>
            <div className={styles.composer}>
              <ChatInputArea.Inner
                onChange={(event) => setInput(event.target.value)}
                onSend={() => setInput("")}
                placeholder="向灵析提问，或描述你想完成的工作…"
                rows={3}
                value={input}
              />
              <div className={styles.composerToolbar}>
                <div>
                  <ActionIcon icon={Paperclip} size="small" title="添加附件" />
                  <ActionIcon icon={Boxes} size="small" title="选择知识库" />
                </div>
                <span>Enter 发送 · Shift + Enter 换行</span>
              </div>
            </div>
            <p className={styles.disclaimer}>AI 生成内容仅供参考，重要信息请核实。</p>
          </div>
        </div>
      </section>
    </main>
  );
}
