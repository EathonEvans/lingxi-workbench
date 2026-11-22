package com.lingxi.workbench.agentruntime.skill;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitAware;
import io.agentscope.core.tool.ToolGroupScope;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 以只读发布快照建立 AgentScope SkillBox。
 *
 * <p>AgentScope 2.0.2 的默认动态中间件会为每个短生命周期 Agent 创建工作目录，且其加载工具
 * 会枚举 Filter 之外的已注册 Skill。本中间件在 Agent 构建时只注册可见快照，并关闭文件上传，
 * 使 Filter 同时约束目录提示和加载工具输入。</p>
 */
public final class AgentScopeSkillMiddleware implements MiddlewareBase, ToolkitAware {

    public static final String SKILL_LOADER_GROUP = "skill-build-in-tools";

    private final AgentScopeSkillCatalog catalog;
    private volatile SkillBox skillBox;

    public AgentScopeSkillMiddleware(AgentScopeSkillCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "AgentScope发布Skill目录不能为空");
    }

    @Override
    public void rebindToolkit(Toolkit toolkit) {
        SkillBox box = new SkillBox(Objects.requireNonNull(toolkit, "AgentScope Toolkit不能为空"));
        catalog.repository().getAllSkills().stream()
                .filter(skill -> catalog.filter().isAllowed(skill.getSkillId()))
                .forEach(box::registerSkill);
        box.setAutoUploadSkill(false);
        if (!box.getAllSkillIds().isEmpty()) {
            // 初始 AgentState 会以 activatedGroups 覆盖 Toolkit；该加载器不交给模型管理，
            // 由 Gateway 显式写入初始状态，并以 EXTERNAL 范围保持为框架固定能力。
            toolkit.createToolGroup(SKILL_LOADER_GROUP, "published skill loader", true,
                    ToolGroupScope.EXTERNAL);
            box.registerSkillLoadTool();
        }
        skillBox = box;
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
        SkillBox box = Objects.requireNonNull(skillBox, "AgentScope发布SkillBox尚未绑定Toolkit");
        String skillPrompt = box.getSkillPrompt(catalog.filter());
        if (skillPrompt.isBlank()) {
            return Mono.just(Objects.requireNonNullElse(currentPrompt, ""));
        }
        String base = Objects.requireNonNullElse(currentPrompt, "");
        String separator = base.isBlank() || base.endsWith("\n") ? "" : "\n";
        return Mono.just(base + separator + skillPrompt);
    }
}
