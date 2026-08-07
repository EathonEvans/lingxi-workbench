package com.lingxi.workbench.admin.agent.model.converter;

import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.config.AgentKnowledgeBinding;
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
                .agentType(agent.getAgentType())
                .status(agent.getStatus())
                .publishedVersionId(stringId(agent.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion)
                        ? null : publishedVersion.getVersionNo())
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
                .agentType(agent.getAgentType())
                .status(agent.getStatus())
                .publishedVersionId(stringId(agent.getPublishedVersionId()))
                .publishedVersionNo(Objects.isNull(publishedVersion)
                        ? null : publishedVersion.getVersionNo())
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
                .rewriteModelInstanceId(stringId(draft.getRewriteModelInstanceId()))
                .systemPromptVersionId(stringId(draft.getSystemPromptVersionId()))
                .userPromptVersionId(stringId(draft.getUserPromptVersionId()))
                .rewritePromptVersionId(stringId(draft.getRewritePromptVersionId()))
                .toolCodes(Objects.requireNonNullElse(draft.getToolCodes(), java.util.List.of()))
                .toolPolicy(draft.getToolPolicy())
                .knowledge(toKnowledgeVO(knowledge))
                .memoryPolicy(draft.getMemoryPolicy())
                .rewritePolicy(draft.getRewritePolicy())
                .retrievalPolicy(draft.getRetrievalPolicy())
                .generationPolicy(draft.getGenerationPolicy())
                .runtimePolicy(draft.getRuntimePolicy())
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
                .agentType(definition.getAgentType())
                .chatModelInstanceId(stringId(definition.getChatModelInstanceId()))
                .rewriteModelInstanceId(stringId(definition.getRewriteModelInstanceId()))
                .systemPromptVersionId(stringId(
                        definition.resolveSystemPromptVersionId()))
                .userPromptVersionId(stringId(definition.getUserPromptVersionId()))
                .rewritePromptVersionId(stringId(definition.getRewritePromptVersionId()))
                .toolCodes(Objects.requireNonNullElse(definition.getToolCodes(), java.util.List.of()))
                .toolPolicy(definition.getToolPolicy())
                .knowledge(toKnowledgeVO(definition.getKnowledge()))
                .knowledgeSearch(definition.getKnowledgeSearch())
                .memoryPolicy(definition.getMemoryPolicy())
                .rewritePolicy(definition.getRewritePolicy())
                .retrievalPolicy(definition.getRetrievalPolicy())
                .generationPolicy(definition.getGenerationPolicy())
                .runtimePolicy(definition.getRuntimePolicy())
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

    private AgentKnowledgeBindingVO toKnowledgeVO(AgentKnowledgeBinding knowledge) {
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
}
