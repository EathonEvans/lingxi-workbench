package com.lingxi.workbench.admin.agent.model.converter;

import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionEntity;
import com.lingxi.workbench.admin.agent.model.vo.AgentDefinitionVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentDraftVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentKnowledgeBindingVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionVO;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;

/** Agent 实体、快照和管理端响应之间的显式转换器。 */
@Component
public class AgentConverter {

    /** 组装 Agent 列表 VO，含生效版本号与草稿版本号。 */
    public AgentVO toListVO(
            AgentEntity agent, AgentVersionEntity publishedVersion, AgentDraftEntity draft) {
        return AgentVO.builder()
                .id(stringId(agent.getId()))
                .name(agent.getName())
                .code(agent.getCode())
                .description(agent.getDescription())
                .role(agent.getRole())
                .domainId(stringId(agent.getDomainId()))
                .status(agent.getStatus())
                .publishedVersionId(stringId(agent.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion)
                        ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(isPublishedVersionReady(agent, publishedVersion))
                .draftRevision(Objects.isNull(draft) ? null : draft.getRevision())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }

    /** 组装 Agent 详情 VO，含主记录、生效版本、草稿和知识库绑定。 */
    public AgentDetailVO toDetailVO(
            AgentEntity agent,
            AgentVersionEntity publishedVersion,
            AgentDraftEntity draft,
            AgentDraftKnowledgeEntity knowledge) {
        return AgentDetailVO.builder()
                .id(stringId(agent.getId()))
                .name(agent.getName())
                .code(agent.getCode())
                .description(agent.getDescription())
                .role(agent.getRole())
                .domainId(stringId(agent.getDomainId()))
                .status(agent.getStatus())
                .publishedVersionId(stringId(agent.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion)
                        ? null : publishedVersion.getVersionNo())
                .publishedVersionReady(isPublishedVersionReady(agent, publishedVersion))
                .draft(toDraftVO(draft, knowledge))
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }

    /** 组装草稿 VO，含策略配置和知识库绑定。 */
    public AgentDraftVO toDraftVO(
            AgentDraftEntity draft, AgentDraftKnowledgeEntity knowledge) {
        return AgentDraftVO.builder()
                .id(stringId(draft.getId()))
                .baseVersionId(stringId(draft.getBaseVersionId()))
                .schemaVersion(draft.getSchemaVersion())
                .chatModelInstanceId(stringId(draft.getChatModelInstanceId()))
                .systemPromptVersionId(stringId(draft.getSystemPromptVersionId()))
                .userPromptVersionId(stringId(draft.getUserPromptVersionId()))
                .toolCodes(Objects.requireNonNullElse(draft.getToolCodes(), java.util.List.of()))
                .skillIds(Objects.requireNonNullElse(draft.getSkillIds(), java.util.List.of()).stream()
                        .map(String::valueOf)
                        .toList())
                .toolPolicy(draft.getToolPolicy())
                .knowledge(toKnowledgeVO(knowledge))
                .retrievalPolicy(draft.getRetrievalPolicy())
                .generationPolicy(draft.getGenerationPolicy())
                .planPolicy(draft.getPlanPolicy())
                .revision(draft.getRevision())
                .changeNote(draft.getChangeNote())
                .build();
    }

    /** 组装版本摘要 VO。 */
    public AgentVersionVO toVersionVO(AgentVersionEntity version) {
        return AgentVersionVO.builder()
                .id(stringId(version.getId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .build();
    }

    /** 组装版本详情 VO，含完整定义快照。 */
    public AgentVersionDetailVO toVersionDetailVO(AgentVersionEntity version) {
        return AgentVersionDetailVO.builder()
                .id(stringId(version.getId()))
                .agentId(stringId(version.getAgentId()))
                .versionNo(version.getVersionNo())
                .versionName(version.getVersionName())
                .schemaVersion(version.getSchemaVersion())
                .definition(toDefinitionVO(version.getDefinitionSnapshot()))
                .snapshotChecksum(version.getSnapshotChecksum())
                .changeNote(version.getChangeNote())
                .publishedBy(stringId(version.getPublishedBy()))
                .publishedAt(version.getPublishedAt())
                .build();
    }

    private AgentDefinitionVO toDefinitionVO(AgentDefinition definition) {
        return AgentDefinitionVO.builder()
                .schemaVersion(definition.getSchemaVersion())
                .role(definition.getRole())
                .chatModelInstanceId(stringId(definition.getChatModelInstanceId()))
                .systemPromptVersionId(stringId(definition.getSystemPromptVersionId()))
                .userPromptVersionId(stringId(definition.getUserPromptVersionId()))
                .toolCodes(Objects.requireNonNullElse(definition.getToolCodes(), java.util.List.of()))
                .toolPolicy(definition.getToolPolicy())
                .skills(Objects.requireNonNullElse(definition.getSkills(), java.util.List.of()))
                .knowledgeSearch(definition.getKnowledgeSearch())
                .generationPolicy(definition.getGenerationPolicy())
                .planPolicy(definition.getPlanPolicy())
                .build();
    }

    private AgentKnowledgeBindingVO toKnowledgeVO(AgentDraftKnowledgeEntity knowledge) {
        if (Objects.isNull(knowledge)) {
            return null;
        }
        return new AgentKnowledgeBindingVO(
                stringId(knowledge.getKnowledgeBaseId()),
                stringId(knowledge.getEmbeddingModelInstanceId()),
                knowledge.getBindingMode(),
                knowledge.getPriority());
    }

    private static String stringId(Long id) {
        return Objects.isNull(id) ? null : String.valueOf(id);
    }

    /** 当前正式版本只有满足最新协议且类型未漂移时，才能被 Application 或 Workflow 引用。 */
    private static boolean isPublishedVersionReady(AgentEntity agent, AgentVersionEntity version) {
        return Objects.nonNull(version)
                && Objects.equals(version.getSchemaVersion(), AGENT_SCHEMA_VERSION)
                && Objects.nonNull(version.getDefinitionSnapshot())
                && Objects.equals(version.getDefinitionSnapshot().getSchemaVersion(), AGENT_SCHEMA_VERSION)
                && version.getDefinitionSnapshot().getRole() == agent.getRole();
    }
}
