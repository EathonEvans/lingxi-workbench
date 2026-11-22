"use client";

import { Button, Tag } from "@lobehub/ui";
import { Drawer, Empty, Input, Spin } from "antd";
import { AlarmClock, History, Pause, Play, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import {
  type TriggerCapabilities,
  type TriggerFire,
  type WorkbenchTrigger,
} from "@/lib/trigger";
import styles from "./trigger-drawer.module.css";

type TriggerDrawerProps = {
  applicationCode: string;
  onClose: () => void;
  onCreate: (payload: Record<string, unknown>) => Promise<boolean>;
  onLoadCapabilities: (applicationCode: string) => Promise<void>;
  onLoadFireHistory: (triggerId: string) => Promise<void>;
  onPause: (triggerId: string) => Promise<void>;
  onRemove: (triggerId: string) => Promise<void>;
  onResume: (triggerId: string) => Promise<void>;
  open: boolean;
  triggerCapabilities?: TriggerCapabilities;
  triggerFires: TriggerFire[];
  triggerFiresLoading?: boolean;
  triggerLoading: boolean;
  triggers: WorkbenchTrigger[];
};

const CRON_PRESETS: Array<{ label: string; value: string }> = [
  { label: "每天 9 点", value: "0 0 9 * * ?" },
  { label: "每个工作日 9 点", value: "0 0 9 ? * MON-FRI" },
  { label: "每周一 9 点", value: "0 0 9 ? * MON" },
];

const FIRE_STATUS_LABELS: Record<string, string> = {
  CANCELLED: "已取消",
  EXPIRED: "已过期",
  FAILED: "失败",
  FIRED: "已触发",
  RUNNING: "执行中",
  SKIPPED: "已跳过",
  SUCCEEDED: "成功",
};

const SKIP_REASON_LABELS: Record<string, string> = {
  CONVERSATION_BUSY: "会话忙",
  DAILY_QUOTA: "当日配额已用尽",
  INTERACTION_PENDING: "待确认交互",
  QUIET_HOURS: "静默时段",
  VERSION_UNAVAILABLE: "绑定版本不可用",
};

function formatDateTime(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("zh-CN", {
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
  });
}

/** 定时任务 Drawer：触发器列表、新建表单与运行历史。 */
export default function TriggerDrawer({
  applicationCode,
  onClose,
  onCreate,
  onLoadCapabilities,
  onLoadFireHistory,
  onPause,
  onRemove,
  onResume,
  open,
  triggerCapabilities,
  triggerFires,
  triggerFiresLoading,
  triggerLoading,
  triggers,
}: TriggerDrawerProps) {
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [name, setName] = useState("");
  const [goalTemplate, setGoalTemplate] = useState("");
  const [cronExpr, setCronExpr] = useState(CRON_PRESETS[0].value);
  const [quietHours, setQuietHours] = useState("");
  const [allowedToolCodes, setAllowedToolCodes] = useState("");
  const [selectedCapability, setSelectedCapability] = useState<{ code: string; type: "AGENT" | "WORKFLOW" } | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const startCreate = async () => {
    setCreating(true);
    if (!triggerCapabilities) {
      await onLoadCapabilities(applicationCode);
    }
  };

  const submitCreate = async () => {
    if (!triggerCapabilities || !name.trim() || !goalTemplate.trim() || !selectedCapability) return;
    setSubmitting(true);
    try {
      const ok = await onCreate({
        allowedToolCodes: allowedToolCodes
          .split(/[,，]/)
          .map((code) => code.trim())
          .filter(Boolean),
        applicationId: Number(triggerCapabilities.applicationId),
        applicationVersionId: Number(triggerCapabilities.applicationVersionId),
        capabilityCode: selectedCapability.code,
        capabilityType: selectedCapability.type,
        cronExpr: cronExpr.trim(),
        goalTemplate: goalTemplate.trim(),
        name: name.trim(),
        quietHours: quietHours.trim() || undefined,
        timezone: "Asia/Shanghai",
      });
      if (ok) {
        setCreating(false);
        setName("");
        setGoalTemplate("");
        setQuietHours("");
        setAllowedToolCodes("");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const toggleHistory = (triggerId: string) => {
    if (expandedId === triggerId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(triggerId);
    void onLoadFireHistory(triggerId);
  };

  return (
    <Drawer
      onClose={onClose}
      open={open}
      styles={{ body: { paddingTop: 12 } }}
      title={
        <span className={styles.title}>
          <AlarmClock aria-hidden="true" size={16} />
          定时任务
        </span>
      }
      width={460}
    >
      {creating ? (
        <div className={styles.form} data-testid="trigger-form">
          <label className={styles.formLabel}>名称</label>
          <Input onChange={(event) => setName(event.target.value)} placeholder="例如：每日工单巡检" value={name} />
          <label className={styles.formLabel}>任务目标</label>
          <Input.TextArea
            onChange={(event) => setGoalTemplate(event.target.value)}
            placeholder="描述每次执行要完成的工作，可使用 {{now}} 表示当前时间"
            rows={3}
            value={goalTemplate}
          />
          <label className={styles.formLabel}>执行频率</label>
          <div className={styles.presetRow}>
            {CRON_PRESETS.map((preset) => (
              <button
                className={`${styles.preset} ${cronExpr === preset.value ? styles.presetActive : ""}`}
                key={preset.value}
                onClick={() => setCronExpr(preset.value)}
                type="button"
              >
                {preset.label}
              </button>
            ))}
          </div>
          <Input onChange={(event) => setCronExpr(event.target.value)} value={cronExpr} />
          <label className={styles.formLabel}>静默时段（可选，如 22:00-08:00）</label>
          <Input onChange={(event) => setQuietHours(event.target.value)} value={quietHours} />
          <label className={styles.formLabel}>预授权工具（可选，逗号分隔；留空表示只读汇报）</label>
          <Input onChange={(event) => setAllowedToolCodes(event.target.value)} value={allowedToolCodes} />
          <label className={styles.formLabel}>执行能力</label>
          {triggerCapabilities ? (
            <div className={styles.capabilityList}>
              {triggerCapabilities.capabilities.length === 0 && (
                <span className={styles.capabilityEmpty}>当前应用没有可绑定的能力</span>
              )}
              {triggerCapabilities.capabilities.map((capability) => (
                <button
                  className={`${styles.preset} ${selectedCapability?.code === capability.code ? styles.presetActive : ""}`}
                  key={capability.code}
                  onClick={() => setSelectedCapability({ code: capability.code, type: capability.type })}
                  type="button"
                >
                  {capability.name}（{capability.type === "AGENT" ? "助手" : "工作流"}）
                </button>
              ))}
            </div>
          ) : (
            <div className={styles.capabilityLoading}>
              <Spin size="small" />
            </div>
          )}
          <div className={styles.formActions}>
            <Button onClick={() => setCreating(false)}>取消</Button>
            <Button
              disabled={!selectedCapability}
              loading={submitting}
              onClick={() => void submitCreate()}
              type="primary"
            >
              创建
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <Button
            className={styles.createButton}
            icon={<Plus size={14} />}
            onClick={() => void startCreate()}
            type="primary"
          >
            新建定时任务
          </Button>
          {triggerLoading && (
            <div className={styles.loading}>
              <Spin />
            </div>
          )}
          {!triggerLoading && triggers.length === 0 && (
            <Empty description="暂无定时任务" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
          {triggers.map((trigger) => (
            <section className={styles.trigger} key={trigger.id}>
              <div className={styles.triggerHeader}>
                <strong>{trigger.name}</strong>
                {trigger.status === "ACTIVE" ? (
                  <Tag color="green">调度中</Tag>
                ) : trigger.status === "PAUSED" ? (
                  <Tag color="orange">已暂停</Tag>
                ) : (
                  <Tag>已归档</Tag>
                )}
              </div>
              <p className={styles.triggerGoal}>{trigger.goalTemplate}</p>
              <div className={styles.triggerMeta}>
                <span>{trigger.cronExpr}</span>
                <span>下次执行：{formatDateTime(trigger.nextFireAt)}</span>
                {trigger.lastRunStatus && <span>上次：{FIRE_STATUS_LABELS[trigger.lastRunStatus] ?? trigger.lastRunStatus}</span>}
              </div>
              {trigger.status === "PAUSED" && trigger.pauseReason && (
                <p className={styles.pauseReason}>{trigger.pauseReason}</p>
              )}
              <div className={styles.triggerActions}>
                {trigger.status === "ACTIVE" ? (
                  <button onClick={() => void onPause(trigger.id)} type="button">
                    <Pause aria-hidden="true" size={13} /> 暂停
                  </button>
                ) : trigger.status === "PAUSED" ? (
                  <button onClick={() => void onResume(trigger.id)} type="button">
                    <Play aria-hidden="true" size={13} /> 恢复
                  </button>
                ) : null}
                <button onClick={() => toggleHistory(trigger.id)} type="button">
                  <History aria-hidden="true" size={13} /> 历史
                </button>
                <button className={styles.danger} onClick={() => void onRemove(trigger.id)} type="button">
                  <Trash2 aria-hidden="true" size={13} /> 删除
                </button>
              </div>
              {expandedId === trigger.id && (
                <div className={styles.fireHistory} data-testid="trigger-fires">
                  {triggerFiresLoading && <span className={styles.fireEmpty}>正在加载…</span>}
                  {!triggerFiresLoading && triggerFires.length === 0 && (
                    <span className={styles.fireEmpty}>暂无运行记录</span>
                  )}
                  {triggerFires.map((fire) => (
                    <div className={styles.fireRow} key={fire.id}>
                      <span>{formatDateTime(fire.plannedAt)}</span>
                      <span>{FIRE_STATUS_LABELS[fire.status] ?? fire.status}</span>
                      {fire.skipReason && (
                        <span>{SKIP_REASON_LABELS[fire.skipReason] ?? fire.skipReason}</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}
    </Drawer>
  );
}
