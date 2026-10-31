"use client";

import { useCallback, useState } from "react";
import type { AuthSession } from "@/lib/auth";
import {
  createTrigger,
  deleteTrigger,
  type TriggerFire,
  listTriggerFires,
  listTriggers,
  pauseTrigger,
  resumeTrigger,
  updateTrigger,
  type WorkbenchTrigger,
} from "@/lib/trigger";

type UseTriggersOptions = {
  onError: (message: string) => void;
  session: AuthSession | null;
};

type UseTriggersReturn = {
  create: (payload: Record<string, unknown>) => Promise<boolean>;
  fireHistory: TriggerFire[];
  fireHistoryLoading: boolean;
  loadFireHistory: (triggerId: string) => Promise<void>;
  loadTriggers: () => Promise<void>;
  loading: boolean;
  pause: (triggerId: string) => Promise<void>;
  remove: (triggerId: string) => Promise<void>;
  resume: (triggerId: string) => Promise<void>;
  triggers: WorkbenchTrigger[];
  update: (triggerId: string, payload: Record<string, unknown>) => Promise<boolean>;
};

/** 定时任务 Drawer 的数据装载与操作编排；所有错误统一回调展示。 */
export function useTriggers({ onError, session }: UseTriggersOptions): UseTriggersReturn {
  const [triggers, setTriggers] = useState<WorkbenchTrigger[]>([]);
  const [loading, setLoading] = useState(false);
  const [fireHistory, setFireHistory] = useState<TriggerFire[]>([]);
  const [fireHistoryLoading, setFireHistoryLoading] = useState(false);

  const loadTriggers = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    try {
      const page = await listTriggers(session, { current: 1, size: 50 });
      setTriggers(page.records);
    } catch (error) {
      onError(error instanceof Error ? error.message : "查询定时任务失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, [onError, session]);

  const loadFireHistory = useCallback(async (triggerId: string) => {
    if (!session) return;
    setFireHistoryLoading(true);
    try {
      const page = await listTriggerFires(triggerId, session, { current: 1, size: 10 });
      setFireHistory(page.records);
    } catch (error) {
      onError(error instanceof Error ? error.message : "查询运行历史失败，请稍后重试");
    } finally {
      setFireHistoryLoading(false);
    }
  }, [onError, session]);

  const create = useCallback(async (payload: Record<string, unknown>) => {
    if (!session) return false;
    try {
      await createTrigger(payload, session);
      await loadTriggers();
      return true;
    } catch (error) {
      onError(error instanceof Error ? error.message : "创建定时任务失败，请稍后重试");
      return false;
    }
  }, [loadTriggers, onError, session]);

  const update = useCallback(async (triggerId: string, payload: Record<string, unknown>) => {
    if (!session) return false;
    try {
      await updateTrigger(triggerId, payload, session);
      await loadTriggers();
      return true;
    } catch (error) {
      onError(error instanceof Error ? error.message : "更新定时任务失败，请稍后重试");
      return false;
    }
  }, [loadTriggers, onError, session]);

  const pause = useCallback(async (triggerId: string) => {
    if (!session) return;
    try {
      await pauseTrigger(triggerId, session);
      await loadTriggers();
    } catch (error) {
      onError(error instanceof Error ? error.message : "暂停任务失败，请稍后重试");
    }
  }, [loadTriggers, onError, session]);

  const resume = useCallback(async (triggerId: string) => {
    if (!session) return;
    try {
      await resumeTrigger(triggerId, session);
      await loadTriggers();
    } catch (error) {
      onError(error instanceof Error ? error.message : "恢复任务失败，请稍后重试");
    }
  }, [loadTriggers, onError, session]);

  const remove = useCallback(async (triggerId: string) => {
    if (!session) return;
    try {
      await deleteTrigger(triggerId, session);
      await loadTriggers();
    } catch (error) {
      onError(error instanceof Error ? error.message : "删除任务失败，请稍后重试");
    }
  }, [loadTriggers, onError, session]);

  return {
    create,
    fireHistory,
    fireHistoryLoading,
    loadFireHistory,
    loadTriggers,
    loading,
    pause,
    remove,
    resume,
    triggers,
    update,
  };
}
