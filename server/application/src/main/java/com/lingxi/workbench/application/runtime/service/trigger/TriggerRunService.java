package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.common.enums.TriggerSkipReason;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.application.runtime.model.PreResolvedDispatch;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.chat.model.bo.ProactiveGenerationRequest;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 定时触发器消费侧执行：加载触发上下文并启动与用户对话同源的主动生成任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerRunService {

    private static final DateTimeFormatter GOAL_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TriggerRepo triggerRepo;
    private final TriggerFireRepo fireRepo;
    private final TriggerProperties properties;
    private final UserRepo userRepo;
    private final ApplicationVersionService appVersionService;
    private final ChatStreamService chatStreamService;

    /**
     * 消费到期触发消息。执行权经触发记录 CAS 抢占（FIRED→RUNNING），重复投递直接跳过。
     */
    public void executeFire(Long fireId, Long triggerId) {
        if (!properties.isEnabled()) {
            return;
        }
        TriggerFireEntity fire = fireRepo.findById(fireId).orElse(null);
        if (Objects.isNull(fire) || fire.getStatus() != TriggerFireStatus.FIRED) {
            log.info("触发记录不存在或已被处理，跳过消费，fireId={}", fireId);
            return;
        }
        TriggerEntity trigger = triggerRepo.findById(triggerId).orElse(null);
        if (Objects.isNull(trigger)) {
            fireRepo.finish(fireId, TriggerFireStatus.EXPIRED.getCode(),
                    null, "触发器不存在或已删除");
            return;
        }
        if (!fireRepo.claimRunning(fireId, null)) {
            log.info("触发记录已被其他消费抢占，fireId={}", fireId);
            return;
        }
        try {
            startProactiveRun(trigger, fireId);
        } catch (BusinessException exception) {
            // 会话忙属于时序冲突：收敛为跳过，不计入连续失败。
            fireRepo.finish(fireId, TriggerFireStatus.SKIPPED.getCode(),
                    TriggerSkipReason.CONVERSATION_BUSY.getCode(),
                    exception.getMessage());
            log.info("主动运行因会话忙被跳过，triggerId={}，fireId={}", triggerId, fireId);
        }
    }

    private void startProactiveRun(TriggerEntity trigger, Long fireId) {
        UserBO user = userRepo.findById(trigger.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "触发器用户不存在"));
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
        AppRuntimeSnapshot snapshot = appVersionService.loadRuntimeAppVersion(
                trigger.getApplicationId(), trigger.getApplicationVersionId());
        CapabilityDefinition capability = resolveCapability(snapshot, trigger);
        ZonedDateTime now = ZonedDateTime.now();
        String query = TriggerScheduleCalculator.renderGoal(trigger.getGoalTemplate(), now);
        chatStreamService.startProactiveGeneration(ProactiveGenerationRequest.builder()
                .application(snapshot)
                .loginUser(loginUser)
                .triggerId(trigger.getId())
                .fireId(fireId)
                .conversationId(trigger.getConversationId())
                .clientMessageId("trigger:" + trigger.getId() + ":" + fireId)
                .query(query)
                .preResolvedDispatch(new PreResolvedDispatch(capability.getCode(), capability.getType(),
                        trigger.getAllowedToolCodes()))
                .build());
    }

    /**
     * 从冻结能力目录解析触发器绑定的能力。
     *
     * <p>目录随 ApplicationVersion 冻结，创建时校验过唯一性，这里按 id 与版本双重匹配。</p>
     */
    private static CapabilityDefinition resolveCapability(AppRuntimeSnapshot snapshot, TriggerEntity trigger) {
        boolean agentMode = "AGENT".equals(trigger.getExecutionMode());
        Long targetId = agentMode ? trigger.getAgentId() : trigger.getWorkflowId();
        Long targetVersionId = agentMode ? trigger.getAgentVersionId() : trigger.getWorkflowVersionId();
        List<CapabilityDefinition> matches = snapshot.definition().capabilityCatalog().capabilities().stream()
                .filter(capability -> capability.getType() == (agentMode
                        ? ApplicationCapabilityType.AGENT : ApplicationCapabilityType.WORKFLOW))
                .filter(capability -> Objects.equals(capability.getTargetId(), targetId)
                        && Objects.equals(capability.getTargetVersionId(), targetVersionId))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("触发器绑定的能力在冻结目录中不存在或不唯一，triggerId=" + trigger.getId());
        }
        return matches.getFirst();
    }
}
