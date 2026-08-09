package com.lingxi.workbench.chat.memory;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.entity.UserProfileEntity;
import com.lingxi.workbench.chat.memory.repo.UserProfileRepo;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileUpdateStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 从完整会话中提取并更新用户画像。 */
@Service
@RequiredArgsConstructor
public class UserProfileMemoryService {

    private final MemoryCompletionService completionService;
    private final UserProfileRepo profileRepo;

    /**
     * 从完整会话历史中提取用户画像并持久化。
     * 先加载既有画像，再调用 LLM 提取更新，根据策略 MERGE 或 REPLACE 后写入。
     */
    public void extract(AssistantCompletedEvent event, String history, UserProfileMemoryConfig config) {
        Map<String, Object> existing = profileRepo.findByUserId(event.userId())
                .map(UserProfileEntity::getProfile)
                .orElse(Map.of());
        String result = completionService.complete(
                config.getPromptVersionId(),
                config.getChatModelInstanceId(),
                Map.of(
                        "existing_profile", JSON.toJSONString(existing),
                        "conversation_history", history));
        Map<String, Object> extracted = JSON.parseObject(result);
        if (extracted != null && !extracted.isEmpty()) {
            Map<String, Object> profile = applyUpdateStrategy(existing, extracted, config.effectiveUpdateStrategy());
            profileRepo.upsert(event.userId(), event.turn().assistantMessageId(), profile);
        }
    }

    private static Map<String, Object> applyUpdateStrategy(Map<String, Object> existing, Map<String, Object> extracted,
                                                            UserProfileUpdateStrategy strategy) {
        if (strategy == UserProfileUpdateStrategy.REPLACE) {
            return extracted;
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(extracted);
        return merged;
    }
}
