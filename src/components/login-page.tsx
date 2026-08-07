"use client";

import { ActionIcon, Button } from "@lobehub/ui";
import {
  ArrowRight,
  BookOpenCheck,
  BrainCircuit,
  Eye,
  EyeOff,
  KeyRound,
  Moon,
  Network,
  ShieldCheck,
  Sparkles,
  Sun,
  UserRound,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, type KeyboardEvent, useEffect, useState } from "react";
import { AuthenticationError } from "@/lib/auth";
import { useAuth } from "./auth-provider";
import { useThemeMode } from "./app-provider";
import styles from "./login-page.module.css";

const capabilities = [
  { icon: BrainCircuit, label: "企业智能体协作" },
  { icon: BookOpenCheck, label: "可信知识库问答" },
  { icon: Network, label: "工具与任务编排" },
];

export function LoginPage() {
  const router = useRouter();
  const { appearance, toggleAppearance } = useThemeMode();
  const { login, ready, session } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (ready && session) router.replace("/");
  }, [ready, router, session]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loading) return;

    setError("");
    setLoading(true);

    try {
      await login({ password, username: username.trim() });
      router.replace("/");
    } catch (loginError) {
      setError(
        loginError instanceof AuthenticationError
          ? loginError.message
          : "登录失败，请稍后重试",
      );
    } finally {
      setLoading(false);
    }
  };

  const handleFormKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
    if (
      event.key !== "Enter" ||
      event.nativeEvent.isComposing ||
      event.target instanceof HTMLButtonElement
    ) {
      return;
    }

    event.preventDefault();
    event.currentTarget.requestSubmit();
  };

  return (
    <main className={styles.page} data-theme={appearance}>
      <div className={styles.ambientOne} />
      <div className={styles.ambientTwo} />

      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandMark}>
            <Sparkles size={19} strokeWidth={2.4} />
          </span>
          <span>
            <strong>灵犀</strong>
            <small>AI Workbench</small>
          </span>
        </div>
        <ActionIcon
          aria-label={appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"}
          icon={appearance === "dark" ? Sun : Moon}
          onClick={toggleAppearance}
          title={appearance === "dark" ? "切换到浅色模式" : "切换到深色模式"}
        />
      </header>

      <section className={styles.introduction}>
        <div className={styles.eyebrow}>
          <span />
          企业级 AI 工作空间
        </div>
        <h1>
          把知识与工具连接起来，
          <span>让工作自然发生。</span>
        </h1>
        <p>
          在一个安全、统一的空间里，与企业智能体协作，检索知识，完成复杂任务。
        </p>

        <div className={styles.capabilities}>
          {capabilities.map(({ icon: Icon, label }) => (
            <div className={styles.capability} key={label}>
              <span>
                <Icon size={18} />
              </span>
              {label}
            </div>
          ))}
        </div>

        <div className={styles.previewCard}>
          <div className={styles.previewHeader}>
            <span className={styles.previewIcon}>
              <Sparkles size={15} />
            </span>
            <span>
              <strong>灵犀助手</strong>
              <small>正在分析你的任务</small>
            </span>
            <i />
          </div>
          <div className={styles.previewLines}>
            <span />
            <span />
            <span />
          </div>
          <div className={styles.previewResult}>
            <ShieldCheck size={15} />
            已连接企业知识库与授权工具
          </div>
        </div>
      </section>

      <section className={styles.loginArea} aria-labelledby="user-login-title">
        <div className={styles.loginCard}>
          <div className={styles.loginHeading}>
            <span className={styles.mobileMark}>
              <Sparkles size={20} />
            </span>
            <p>欢迎回来</p>
            <h2 id="user-login-title">登录灵犀工作台</h2>
            <span>使用公司为你分配的账号继续</span>
          </div>

          <form onKeyDown={handleFormKeyDown} onSubmit={handleSubmit}>
            <label className={styles.field} htmlFor="username">
              <span>账号</span>
              <span className={styles.inputWrap}>
                <UserRound size={17} />
                <input
                  autoComplete="username"
                  autoFocus
                  id="username"
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="请输入账号"
                  required
                  value={username}
                />
              </span>
            </label>

            <label className={styles.field} htmlFor="password">
              <span>密码</span>
              <span className={styles.inputWrap}>
                <KeyRound size={17} />
                <input
                  autoComplete="current-password"
                  id="password"
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="请输入密码"
                  required
                  type={passwordVisible ? "text" : "password"}
                  value={password}
                />
                <button
                  aria-label={passwordVisible ? "隐藏密码" : "显示密码"}
                  className={styles.passwordToggle}
                  onClick={() => setPasswordVisible((visible) => !visible)}
                  type="button"
                >
                  {passwordVisible ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </span>
            </label>

            <div className={styles.formMeta}>
              <span>
                <ShieldCheck size={14} />
                安全加密连接
              </span>
              <span>忘记密码请联系管理员</span>
            </div>

            <div aria-live="polite" className={styles.error} role="status">
              {error}
            </div>

            <Button
              block
              className={styles.submitButton}
              htmlType="submit"
              loading={loading}
              type="primary"
            >
              {loading ? "正在登录…" : "进入工作台"}
              {!loading && <ArrowRight size={17} />}
            </Button>
          </form>

          <p className={styles.legal}>登录即表示你同意遵守公司的数据安全与使用规范</p>
        </div>
      </section>
    </main>
  );
}
