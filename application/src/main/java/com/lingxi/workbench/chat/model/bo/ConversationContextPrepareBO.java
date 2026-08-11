package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.framework.context.LoginUser;
import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

/** 构建单轮共享对话上下文所需的可信输入。 */
public final class ConversationContextPrepareBO {

    private final AppRuntimeSnapshot application;
    private final String query;
    private final LoginUser loginUser;
    private final ChatTurnBO turn;
    private final Instant startedAt;

    @Builder
    private ConversationContextPrepareBO(AppRuntimeSnapshot application, String query, LoginUser loginUser,
                                         ChatTurnBO turn, Instant startedAt) {
        this.application = Objects.requireNonNull(application, "Application运行快照不能为空");
        this.query = requireText(query);
        this.loginUser = Objects.requireNonNull(loginUser, "登录用户不能为空");
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.startedAt = Objects.requireNonNull(startedAt, "执行开始时间不能为空");
    }

    public AppRuntimeSnapshot application() {
        return application;
    }

    public String query() {
        return query;
    }

    public LoginUser loginUser() {
        return loginUser;
    }

    public ChatTurnBO turn() {
        return turn;
    }

    public Instant startedAt() {
        return startedAt;
    }

    private static String requireText(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException("用户问题不能为空");
        }
        return value.trim();
    }
}
