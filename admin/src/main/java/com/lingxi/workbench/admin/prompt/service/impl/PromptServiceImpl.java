package com.lingxi.workbench.admin.prompt.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.converter.PromptConverter;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.model.request.SavePromptRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.AvailablePromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptDetailVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import com.lingxi.workbench.admin.prompt.repo.PromptRepo;
import com.lingxi.workbench.admin.prompt.repo.PromptVersionRepo;
import com.lingxi.workbench.admin.prompt.service.PromptService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Prompt模板管理业务实现。 */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private final PromptRepo promptRepo;
    private final PromptVersionRepo versionRepo;
    private final PromptConverter promptConverter;
    private final AuditService auditService;
    private final PromptTemplateProcessor templateProcessor;

    @Override
    @Transactional(readOnly = true)
    public PageResult<PromptVO> page(PromptPageQuery query) {
        PageResult<PromptEntity> page = promptRepo.findPage(query);
        List<Long> promptIds = page.records().stream().map(PromptEntity::getId).toList();
        // 批量查询版本信息，补充列表页需要的版本号和已发布版本号
        Map<Long, List<PromptVersionEntity>> versionsByPromptId = versionRepo
                .findAllByPromptIds(promptIds)
                .stream()
                .collect(Collectors.groupingBy(PromptVersionEntity::getPromptId));
        return page.map(entity -> {
            List<PromptVersionEntity> versions =
                    versionsByPromptId.getOrDefault(entity.getId(), List.of());
            return promptConverter.toPromptVO(
                    entity,
                    publishedVersionNo(entity, versions),
                    draftVersion(versions)
                            .map(PromptVersionEntity::getVersionNo)
                            .orElse(null));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PromptDetailVO detail(Long id) {
        PromptEntity prompt = requirePrompt(id);
        List<PromptVersionEntity> versions = versionRepo.findAll(id);
        // 优先取草稿版本，没有草稿时回退到已发布版本
        PromptVersionEntity editingVersion = draftVersion(versions)
                .orElseGet(() -> publishedVersion(prompt, versions));
        return promptConverter.toDetailVO(
                prompt,
                publishedVersionNo(prompt, versions),
                editingVersion.getStatus() == PromptVersionStatus.DRAFT
                        ? String.valueOf(editingVersion.getId())
                        : null,
                editingVersion.getStatus() == PromptVersionStatus.DRAFT
                        ? editingVersion.getVersionNo()
                        : null,
                editingVersion.getPromptContent(),
                promptConverter.toVariableVOs(editingVersion.getVariables()),
                editingVersion.getChangeNote());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptVersionVO> versions(Long id) {
        requirePrompt(id);
        return versionRepo.findAll(id).stream().map(promptConverter::toVersionVO).toList();
    }

    @Override
    public List<AvailablePromptVariableVO> availableVariables() {
        return templateProcessor.availableVariables();
    }

    @Override
    @Transactional
    public PromptDetailVO save(Long id, SavePromptRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        // 先校验模板内容和变量声明一致性，再检查编码冲突
        templateProcessor.validate(request.getPromptContent(), request.getVariables());
        if (promptRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Prompt编码已存在");
        }

        PromptEntity prompt = Objects.isNull(id) ? new PromptEntity() : requirePrompt(id);
        if (Objects.nonNull(id) && !prompt.getCode().equals(code)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "已创建的Prompt不允许修改编码");
        }
        prompt.setName(request.getName().trim());
        prompt.setCode(code);
        prompt.setRole(request.getRole());
        prompt.setDescription(trimToNull(request.getDescription()));
        prompt.setUpdatedBy(operatorId);
        if (Objects.isNull(id)) {
            prompt.setStatus(EnableStatus.ACTIVE);
            prompt.setCreatedBy(operatorId);
        }
        promptRepo.save(prompt);

        // 复用已有草稿或新建草稿版本
        PromptVersionEntity draft = versionRepo.findDraft(prompt.getId())
                .orElseGet(() -> newDraft(prompt.getId(), operatorId));
        // Prompt中的首尾空白可能具有语义，持久化时不得擅自裁剪正文。
        draft.setPromptContent(request.getPromptContent());
        draft.setVariables(promptConverter.toDefinitions(request.getVariables()));
        draft.setChangeNote(trimToNull(request.getChangeNote()));
        draft.setUpdatedBy(operatorId);
        versionRepo.save(draft);

        auditService.record(
                "PROMPT",
                "PROMPT",
                prompt.getId(),
                operatorId,
                Objects.isNull(id) ? "PROMPT_CREATE" : "PROMPT_UPDATE_DRAFT",
                "Prompt编码=" + code + "，草稿版本=V" + draft.getVersionNo());
        return detail(prompt.getId());
    }

    @Override
    @Transactional
    public void publish(Long id, Long operatorId) {
        PromptEntity prompt = requirePrompt(id);
        if (prompt.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的Prompt不能发布");
        }
        PromptVersionEntity draft = versionRepo.findDraft(id)
                .orElseThrow(
                        () -> new BusinessException(
                                HttpStatusEnum.CONFLICT, "当前没有可发布的草稿"));
        // 发布前再次校验模板内容与变量一致性
        templateProcessor.validate(
                draft.getPromptContent(), promptConverter.toVariableRequests(draft.getVariables()));

        draft.setStatus(PromptVersionStatus.PUBLISHED);
        draft.setPublishedAt(OffsetDateTime.now());
        draft.setUpdatedBy(operatorId);
        versionRepo.save(draft);
        prompt.setPublishedVersionId(draft.getId());
        prompt.setUpdatedBy(operatorId);
        promptRepo.save(prompt);
        auditService.record(
                "PROMPT",
                "PROMPT",
                id,
                operatorId,
                "PROMPT_PUBLISH",
                "Prompt编码=" + prompt.getCode() + "，发布版本=V" + draft.getVersionNo());
    }

    @Override
    @Transactional
    public void activateVersion(Long id, Long versionId, Long operatorId) {
        PromptEntity prompt = requirePrompt(id);
        if (prompt.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的Prompt不能切换版本");
        }
        PromptVersionEntity version = versionRepo.findByPromptIdAndId(id, versionId)
                .filter(item -> item.getStatus() == PromptVersionStatus.PUBLISHED)
                .orElseThrow(
                        () -> new BusinessException(
                                HttpStatusEnum.NOT_FOUND, "已发布版本不存在"));
        prompt.setPublishedVersionId(versionId);
        prompt.setUpdatedBy(operatorId);
        promptRepo.save(prompt);
        auditService.record(
                "PROMPT",
                "PROMPT",
                id,
                operatorId,
                "PROMPT_VERSION_ACTIVATE",
                "Prompt编码=" + prompt.getCode() + "，生效版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        PromptEntity prompt = requirePrompt(id);
        prompt.setStatus(status);
        prompt.setUpdatedBy(operatorId);
        promptRepo.save(prompt);
        auditService.record(
                "PROMPT",
                "PROMPT",
                id,
                operatorId,
                "PROMPT_STATUS_CHANGE",
                "Prompt编码=" + prompt.getCode() + "，状态=" + status);
    }

    @Override
    public PreviewPromptVO preview(PreviewPromptRequest request) {
        return templateProcessor.preview(request);
    }

    /** 创建新的草稿版本实体。 */
    private PromptVersionEntity newDraft(Long promptId, Long operatorId) {
        PromptVersionEntity draft = new PromptVersionEntity();
        draft.setPromptId(promptId);
        draft.setVersionNo(versionRepo.nextVersionNo(promptId));
        draft.setStatus(PromptVersionStatus.DRAFT);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        return draft;
    }

    /** 按主键查询Prompt，不存在时抛出异常。 */
    private PromptEntity requirePrompt(Long id) {
        return promptRepo.findById(id)
                .orElseThrow(
                        () -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Prompt不存在"));
    }

    /** 从版本列表中获取当前已发布版本。 */
    private PromptVersionEntity publishedVersion(
            PromptEntity prompt, List<PromptVersionEntity> versions) {
        if (prompt.getPublishedVersionId() == null) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "Prompt尚未创建草稿或发布版本");
        }
        return versions.stream()
                .filter(version -> version.getId().equals(prompt.getPublishedVersionId()))
                .findFirst()
                .orElseThrow(
                        () -> new BusinessException(
                                HttpStatusEnum.CONFLICT, "Prompt正式版本数据不完整"));
    }

    /** 从版本列表中提取草稿版本。 */
    private static Optional<PromptVersionEntity> draftVersion(
            List<PromptVersionEntity> versions) {
        return versions.stream()
                .filter(version -> version.getStatus() == PromptVersionStatus.DRAFT)
                .findFirst();
    }

    /** 从版本列表中获取已发布版本的版本号。 */
    private static Integer publishedVersionNo(
            PromptEntity prompt, List<PromptVersionEntity> versions) {
        if (prompt.getPublishedVersionId() == null) {
            return null;
        }
        return versions.stream()
                .filter(version -> version.getId().equals(prompt.getPublishedVersionId()))
                .map(PromptVersionEntity::getVersionNo)
                .findFirst()
                .orElse(null);
    }

    /** 去除首尾空白，空字符串转为null。 */
    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
