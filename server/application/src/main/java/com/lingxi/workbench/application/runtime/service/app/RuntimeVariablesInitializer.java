package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.context.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * 在对话入口创建基础运行时变量快照。
 *
 * <p>用户和时间变量在进入异步 Workflow 前只读取一次。后续节点共享同一快照，
 * 不再依赖请求线程的 UserContext，也不会在 Prompt 渲染期间产生隐藏查询。</p>
 */
@Component
public class RuntimeVariablesInitializer {

    private final Clock clock;
    private final String platformName;
    private final Locale language;

    /**
     * 使用当前 JVM 时区、语言和 Spring 应用名创建生产初始化器。
     */
    @Autowired
    public RuntimeVariablesInitializer(@Value("${spring.application.name:Lingxi Workbench}") String platformName) {
        this(Clock.systemDefaultZone(), platformName, Locale.getDefault());
    }

    /**
     * 测试可以传入固定时钟和语言，保证变量快照可稳定复现。
     */
    public RuntimeVariablesInitializer(Clock clock, String platformName, Locale language) {
        this.clock = clock;
        this.platformName = platformName;
        this.language = language;
    }

    /**
     * 创建本轮对话的基础变量，历史消息由历史加载节点稍后写入。
     *
     * @param loginUser 请求入口冻结的登录用户
     * @param query     用户本轮原始问题
     * @param sessionId 当前会话主键
     * @return 单次执行唯一的运行时变量容器
     */
    public RuntimeVariables initialize(LoginUser loginUser, String query, Long sessionId) {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.SYSTEM_CURRENT_TIME, OffsetDateTime.now(clock).toString());
        variables.put(RuntimeVariableAccessors.SYSTEM_CURRENT_DATE, LocalDate.now(clock).toString());
        variables.put(RuntimeVariableAccessors.SYSTEM_TIMEZONE, clock.getZone().getId());
        variables.put(RuntimeVariableAccessors.SYSTEM_LANGUAGE, language.toLanguageTag());
        variables.put(RuntimeVariableAccessors.SYSTEM_PLATFORM_NAME, platformName);
        variables.put(RuntimeVariableAccessors.USER_ID, loginUser.id());
        variables.put(RuntimeVariableAccessors.USER_NAME, loginUser.displayName());
        variables.put(RuntimeVariableAccessors.USER_QUERY, query);
        variables.put(RuntimeVariableAccessors.SESSION_ID, sessionId);
        return variables;
    }
}
