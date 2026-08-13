package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.config.MemoryEmbeddingModelResolver;
import com.lingxi.workbench.chat.memory.model.MemoryData;
import com.lingxi.workbench.chat.memory.model.entity.InteractionRecordEntity;
import com.lingxi.workbench.chat.memory.repo.InteractionRecordVectorRepo;
import com.lingxi.workbench.chat.memory.repo.UserProfileRepo;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 读取用户画像和相关交互记录，构建本轮可注入的长期记忆上下文。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryContextService {

    private final UserProfileRepo profileRepo;
    private final InteractionRecordVectorRepo vectorRepo;
    private final EmbeddingGateway embeddingGateway;
    private final MemoryEmbeddingModelResolver embeddingResolver;

    public String build(Long userId, String query, MemoryConfig config) {
        StringBuilder context = new StringBuilder();
        if (config.profile().isEnabled()) {
            appendProfile(userId, context);
        }
        if (config.interactionRecord().isEnabled()) {
            appendRelevantRecords(userId, query, config.interactionRecord(), context);
        }
        return context.toString().trim();
    }

    private void appendProfile(Long userId, StringBuilder context) {
        profileRepo.findByUserId(userId).ifPresent(profile -> {
            MemoryData data = MemoryData.from(profile.getProfile());
            if (data.isEmpty()) {
                return;
            }
            // 有界序列化：画像超长或字段异常时截断，防止失控内容进入提示词
            context.append("【用户画像】\n")
                    .append(data.toPromptJson())
                    .append("\n");
        });
    }

    private void appendRelevantRecords(Long userId, String query, InteractionRecordMemoryConfig config, StringBuilder context) {
        try {
            EmbeddingModelRuntimeConfig embeddingConfig = embeddingResolver.resolve(config.getEmbeddingModelInstanceId());
            List<float[]> vectors = embeddingGateway.embed(List.of(query), embeddingConfig);
            if (vectors.isEmpty()) {
                return;
            }
            List<InteractionRecordEntity> records = vectorRepo.findSimilar(
                    userId,
                    config.getEmbeddingModelInstanceId(),
                    vectors.getFirst(),
                    config.effectiveRetentionDays(),
                    config.effectiveTopK());
            if (records.isEmpty()) {
                return;
            }
            context.append("【历史交互记录】\n");
            for (InteractionRecordEntity record : records) {
                context.append("- ").append(record.getSummary()).append("\n");
            }
        } catch (RuntimeException exception) {
            log.warn("长期记忆检索失败，已跳过交互记录注入，userId={}", userId, exception);
        }
    }
}
