package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.MemoryData;
import com.lingxi.workbench.chat.memory.model.entity.UserProfileEntity;
import com.lingxi.workbench.chat.memory.repo.UserProfileRepo;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileUpdateStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    public void extract(AssistantCompletedEvent event, String history, MemoryConfig memoryConfig) {
        UserProfileMemoryConfig config = memoryConfig.profile();
        Optional<UserProfileEntity> optionalProfile = profileRepo.findByUserId(event.userId());
        Long sourceMessageId = optionalProfile.map(UserProfileEntity::getSourceMessageId).orElse(null);
        // 在调用模型前检查水位，避免 RocketMQ 重投或乱序事件产生重复费用。
        if (Objects.nonNull(sourceMessageId) && sourceMessageId >= event.turn().assistantMessageId()) {
            return;
        }
        MemoryData existing = optionalProfile
                .map(UserProfileEntity::getProfile)
                .map(MemoryData::from)
                .orElse(MemoryData.empty());
        String result = completionService.complete(
                config.getPromptVersionId(),
                memoryConfig.getChatModelInstanceId(),
                Map.of(
                        "existing_profile", existing.toPromptJson(),
                        "conversation_history", history));
        MemoryData extracted = MemoryData.fromJson(result);
        if (extracted.isEmpty()) {
            return;
        }
        if (config.effectiveUpdateStrategy() == UserProfileUpdateStrategy.REPLACE) {
            profileRepo.replace(event.userId(), event.turn().assistantMessageId(), extracted.asObjectMap());
            return;
        }
        // MERGE 必须由数据库原子完成，避免不同会话的异步画像任务互相覆盖。
        profileRepo.merge(event.userId(), event.turn().assistantMessageId(), extracted.asObjectMap());
    }
}
