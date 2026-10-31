package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.common.enums.TriggerStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.application.runtime.service.trigger.model.request.CreateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.request.UpdateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerCapabilitiesVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerCapabilityVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerFireVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerVO;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/** 定时触发器业务实现。 */
@Service
@RequiredArgsConstructor
public class TriggerServiceImpl implements TriggerService {

    private final TriggerRepo triggerRepo;
    private final TriggerFireRepo fireRepo;
    private final TriggerProperties properties;
    private final ApplicationVersionService appVersionService;
    private final ConversationService conversationService;

    @Override
    @Transactional
    public TriggerVO create(Long userId, CreateTriggerRequest request) {
        validateSchedule(request.getCronExpr(), request.getTimezone(), request.getQuietHours());
        if (triggerRepo.countActiveByUser(userId) >= properties.getMaxActivePerUser()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "触发器数量已达上限");
        }
        // 绑定冻结版本并校验执行目标在能力目录中唯一存在，运行期不再变更。
        AppRuntimeSnapshot snapshot = appVersionService.loadRuntimeAppVersion(
                request.getApplicationId(), request.getApplicationVersionId());
        CapabilityDefinition capability = resolveBoundCapability(snapshot, request);

        Long conversationId = conversationService.createTriggerConversation(userId, request.getName());
        boolean agentMode = capability.getType() == ApplicationCapabilityType.AGENT;
        TriggerEntity entity = new TriggerEntity();
        entity.setUserId(userId);
        entity.setApplicationId(request.getApplicationId());
        entity.setApplicationVersionId(request.getApplicationVersionId());
        entity.setExecutionMode(agentMode ? "AGENT" : "WORKFLOW");
        entity.setAgentId(agentMode ? capability.getTargetId() : null);
        entity.setAgentVersionId(agentMode ? capability.getTargetVersionId() : null);
        entity.setWorkflowId(agentMode ? null : capability.getTargetId());
        entity.setWorkflowVersionId(agentMode ? null : capability.getTargetVersionId());
        entity.setConversationId(conversationId);
        entity.setName(request.getName().trim());
        entity.setGoalTemplate(request.getGoalTemplate().trim());
        entity.setCronExpr(request.getCronExpr().trim());
        entity.setTimezone(request.getTimezone() == null || request.getTimezone().isBlank()
                ? "Asia/Shanghai" : request.getTimezone().trim());
        entity.setQuietHours(request.getQuietHours());
        entity.setAllowedToolCodes(request.getAllowedToolCodes());
        entity.setStatus(TriggerStatus.ACTIVE);
        entity.setNextFireAt(TriggerScheduleCalculator.nextFire(
                entity.getCronExpr(), entity.getTimezone(), ZonedDateTime.now()).toOffsetDateTime());
        entity.setConsecutiveFailures(0);
        triggerRepo.insert(entity);
        return toVO(entity, capability.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TriggerVO> page(Long userId, PageQuery query) {
        return triggerRepo.findPage(userId, query).map(trigger -> toVO(trigger, null));
    }

    @Override
    @Transactional(readOnly = true)
    public TriggerCapabilitiesVO listCapabilities(Long userId, String applicationCode) {
        // 按当前发布版本列出可绑定能力；能力目录随版本冻结，创建后不受重新发布影响。
        AppRuntimeSnapshot snapshot = appVersionService.loadRuntimeAppConfig(applicationCode);
        TriggerCapabilitiesVO result = new TriggerCapabilitiesVO();
        result.setApplicationId(String.valueOf(snapshot.applicationId()));
        result.setApplicationVersionId(String.valueOf(snapshot.versionId()));
        result.setCapabilities(snapshot.definition().capabilityCatalog().capabilities().stream()
                .filter(capability -> capability.getType() == ApplicationCapabilityType.AGENT
                        || capability.getType() == ApplicationCapabilityType.WORKFLOW)
                .map(capability -> {
                    TriggerCapabilityVO vo = new TriggerCapabilityVO();
                    vo.setCode(capability.getCode());
                    vo.setName(capability.getName());
                    vo.setType(capability.getType().name());
                    vo.setTargetVersionId(String.valueOf(capability.getTargetVersionId()));
                    return vo;
                })
                .toList());
        return result;
    }

    @Override
    @Transactional
    public void update(Long userId, Long triggerId, UpdateTriggerRequest request) {
        validateSchedule(request.getCronExpr(), request.getTimezone(), request.getQuietHours());
        TriggerEntity entity = requireOwned(userId, triggerId);
        entity.setName(request.getName().trim());
        entity.setGoalTemplate(request.getGoalTemplate().trim());
        entity.setCronExpr(request.getCronExpr().trim());
        entity.setTimezone(request.getTimezone() == null || request.getTimezone().isBlank()
                ? entity.getTimezone() : request.getTimezone().trim());
        entity.setQuietHours(request.getQuietHours());
        entity.setAllowedToolCodes(request.getAllowedToolCodes());
        if (entity.getStatus() == TriggerStatus.ACTIVE) {
            entity.setNextFireAt(TriggerScheduleCalculator.nextFire(
                    entity.getCronExpr(), entity.getTimezone(), ZonedDateTime.now()).toOffsetDateTime());
        }
        triggerRepo.save(entity);
    }

    @Override
    @Transactional
    public void pause(Long userId, Long triggerId) {
        TriggerEntity entity = requireOwned(userId, triggerId);
        if (entity.getStatus() != TriggerStatus.ACTIVE) {
            return;
        }
        entity.setStatus(TriggerStatus.PAUSED);
        entity.setPauseReason("用户手动暂停");
        triggerRepo.save(entity);
    }

    @Override
    @Transactional
    public void resume(Long userId, Long triggerId) {
        TriggerEntity entity = requireOwned(userId, triggerId);
        OffsetDateTime nextFireAt = TriggerScheduleCalculator.nextFire(
                entity.getCronExpr(), entity.getTimezone(), ZonedDateTime.now()).toOffsetDateTime();
        if (!triggerRepo.resume(triggerId, nextFireAt)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "触发器当前状态不支持恢复");
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long triggerId) {
        TriggerEntity entity = requireOwned(userId, triggerId);
        entity.setDeletedAt(OffsetDateTime.now());
        triggerRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TriggerFireVO> firePage(Long userId, Long triggerId, PageQuery query) {
        requireOwned(userId, triggerId);
        return fireRepo.findPageByTrigger(triggerId, query).map(this::toFireVO);
    }

    /** 校验调度参数：cron 合法、时区合法、静默时段格式合法。 */
    private void validateSchedule(String cronExpr, String timezone, String quietHours) {
        TriggerScheduleCalculator.parseCron(cronExpr);
        java.time.ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim());
        TriggerScheduleCalculator.shiftIfQuietHours(ZonedDateTime.now(), quietHours);
    }

    /** 校验执行目标在冻结能力目录中唯一存在，避免运行期才暴露配置错误。 */
    private static CapabilityDefinition resolveBoundCapability(AppRuntimeSnapshot snapshot, CreateTriggerRequest request) {
        ApplicationCapabilityType expectedType = "AGENT".equals(request.getCapabilityType())
                ? ApplicationCapabilityType.AGENT : ApplicationCapabilityType.WORKFLOW;
        List<CapabilityDefinition> matches = snapshot.definition().capabilityCatalog().capabilities().stream()
                .filter(capability -> capability.getType() == expectedType
                        && Objects.equals(capability.getCode(), request.getCapabilityCode()))
                .toList();
        if (matches.size() != 1) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "执行目标在应用版本能力目录中不存在或不唯一");
        }
        return matches.getFirst();
    }

    private TriggerEntity requireOwned(Long userId, Long triggerId) {
        return triggerRepo.findOwnedById(triggerId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "触发器不存在"));
    }

    private TriggerVO toVO(TriggerEntity entity, String capabilityName) {
        TriggerVO vo = new TriggerVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus().getCode());
        vo.setExecutionMode(entity.getExecutionMode());
        vo.setCapabilityName(capabilityName);
        vo.setConversationId(String.valueOf(entity.getConversationId()));
        vo.setGoalTemplate(entity.getGoalTemplate());
        vo.setCronExpr(entity.getCronExpr());
        vo.setTimezone(entity.getTimezone());
        vo.setQuietHours(entity.getQuietHours());
        vo.setAllowedToolCodes(entity.getAllowedToolCodes());
        vo.setNextFireAt(entity.getNextFireAt());
        vo.setLastRunStatus(entity.getLastRunStatus());
        vo.setLastRunAt(entity.getLastRunAt());
        vo.setConsecutiveFailures(entity.getConsecutiveFailures());
        vo.setPauseReason(entity.getPauseReason());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private TriggerFireVO toFireVO(TriggerFireEntity entity) {
        TriggerFireVO vo = new TriggerFireVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setTriggerId(String.valueOf(entity.getTriggerId()));
        vo.setPlannedAt(entity.getPlannedAt());
        vo.setFiredAt(entity.getFiredAt());
        vo.setStatus(entity.getStatus().getCode());
        vo.setSkipReason(entity.getSkipReason());
        vo.setErrorMessage(entity.getErrorMessage());
        return vo;
    }
}
