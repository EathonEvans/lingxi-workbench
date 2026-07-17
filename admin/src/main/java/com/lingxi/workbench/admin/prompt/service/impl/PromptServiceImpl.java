package com.lingxi.workbench.admin.prompt.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.converter.PromptConverter;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptPageQuery;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.request.SavePromptRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Prompt 管理业务实现。 */
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*}}");

    private final PromptRepo promptRepo;
    private final PromptVersionRepo versionRepo;
    private final PromptConverter promptConverter;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<PromptVO> page(PromptPageQuery query) {
        PageResult<PromptEntity> page = promptRepo.findPage(query);
        List<Long> promptIds = page.records().stream().map(PromptEntity::getId).toList();
        Map<Long, List<PromptVersionEntity>> versionsByPromptId = versionRepo
                .findAllByPromptIds(promptIds)
                .stream()
                .collect(Collectors.groupingBy(PromptVersionEntity::getPromptId));
        return page.map(entity -> {
            List<PromptVersionEntity> versions = versionsByPromptId.getOrDefault(entity.getId(), List.of());
            return promptConverter.toPromptVO(
                    entity,
                    publishedVersionNo(entity, versions),
                    draftVersion(versions).map(PromptVersionEntity::getVersionNo).orElse(null));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PromptDetailVO detail(Long id) {
        PromptEntity prompt = requirePrompt(id);
        List<PromptVersionEntity> versions = versionRepo.findAll(id);
        PromptVersionEntity editingVersion = draftVersion(versions)
                .orElseGet(() -> publishedVersion(prompt, versions));
        return promptConverter.toDetailVO(
                prompt,
                publishedVersionNo(prompt, versions),
                editingVersion.getStatus() == PromptVersionStatus.DRAFT
                        ? String.valueOf(editingVersion.getId()) : null,
                editingVersion.getStatus() == PromptVersionStatus.DRAFT
                        ? editingVersion.getVersionNo() : null,
                editingVersion.getPromptContent(),
                promptConverter.parseVariables(editingVersion.getVariables()),
                editingVersion.getChangeNote());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptVersionVO> versions(Long id) {
        requirePrompt(id);
        return versionRepo.findAll(id).stream().map(promptConverter::toVersionVO).toList();
    }

    @Override
    @Transactional
    public PromptDetailVO save(Long id, SavePromptRequest request, Long operatorId) {
        String code = request.code().trim().toLowerCase();
        validateTemplate(request.promptContent(), request.variables());
        if (promptRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Prompt 编码已存在");
        }

        PromptEntity prompt = id == null ? new PromptEntity() : requirePrompt(id);
        if (id != null && !prompt.getCode().equals(code)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "已创建的 Prompt 不允许修改编码");
        }
        prompt.setName(request.name().trim());
        prompt.setCode(code);
        prompt.setDescription(trimToNull(request.description()));
        prompt.setUpdatedBy(operatorId);
        if (id == null) {
            prompt.setStatus(EnableStatus.ACTIVE);
            prompt.setCreatedBy(operatorId);
        }
        promptRepo.save(prompt);

        PromptVersionEntity draft = versionRepo.findDraft(prompt.getId())
                .orElseGet(() -> newDraft(prompt.getId(), operatorId));
        // Prompt 中的首尾空白可能具有语义，持久化时不得擅自裁剪正文。
        draft.setPromptContent(request.promptContent());
        draft.setVariables(JSON.toJSONString(request.variables()));
        draft.setChangeNote(trimToNull(request.changeNote()));
        draft.setUpdatedBy(operatorId);
        versionRepo.save(draft);

        auditService.record(
                "PROMPT", "PROMPT", prompt.getId(), operatorId,
                id == null ? "PROMPT_CREATE" : "PROMPT_UPDATE_DRAFT",
                "Prompt编码=" + code + "，草稿版本=V" + draft.getVersionNo());
        return detail(prompt.getId());
    }

    @Override
    @Transactional
    public void publish(Long id, Long operatorId) {
        PromptEntity prompt = requirePrompt(id);
        if (prompt.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的 Prompt 不能发布");
        }
        PromptVersionEntity draft = versionRepo.findDraft(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "当前没有可发布的草稿"));
        validateTemplate(draft.getPromptContent(), parseVariableRequests(draft.getVariables()));

        draft.setStatus(PromptVersionStatus.PUBLISHED);
        draft.setPublishedAt(OffsetDateTime.now());
        draft.setUpdatedBy(operatorId);
        versionRepo.save(draft);
        prompt.setPublishedVersionId(draft.getId());
        prompt.setUpdatedBy(operatorId);
        promptRepo.save(prompt);
        auditService.record(
                "PROMPT", "PROMPT", id, operatorId, "PROMPT_PUBLISH",
                "Prompt编码=" + prompt.getCode() + "，发布版本=V" + draft.getVersionNo());
    }

    @Override
    @Transactional
    public void activateVersion(Long id, Long versionId, Long operatorId) {
        PromptEntity prompt = requirePrompt(id);
        if (prompt.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的 Prompt 不能切换版本");
        }
        PromptVersionEntity version = versionRepo.findByPromptIdAndId(id, versionId)
                .filter(item -> item.getStatus() == PromptVersionStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "已发布版本不存在"));
        prompt.setPublishedVersionId(versionId);
        prompt.setUpdatedBy(operatorId);
        promptRepo.save(prompt);
        auditService.record(
                "PROMPT", "PROMPT", id, operatorId, "PROMPT_VERSION_ACTIVATE",
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
                "PROMPT", "PROMPT", id, operatorId, "PROMPT_STATUS_CHANGE",
                "Prompt编码=" + prompt.getCode() + "，状态=" + status);
    }

    @Override
    public PreviewPromptVO preview(PreviewPromptRequest request) {
        validateTemplate(request.promptContent(), request.variables());
        Map<String, PromptVariableRequest> declarations = request.variables().stream()
                .collect(Collectors.toMap(
                        PromptVariableRequest::name,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> unknownNames = new HashSet<>(request.values().keySet());
        unknownNames.removeAll(declarations.keySet());
        if (!unknownNames.isEmpty()) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "存在未声明的变量：" + String.join("、", unknownNames));
        }

        Map<String, String> renderValues = new HashMap<>();
        declarations.forEach((name, declaration) -> {
            String suppliedValue = trimToNull(request.values().get(name));
            String defaultValue = trimToNull(declaration.defaultValue());
            String value = suppliedValue == null ? defaultValue : suppliedValue;
            if (declaration.required() && value == null) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "变量 " + name + " 不能为空");
            }
            renderValues.put(name, value == null ? "" : value);
        });

        Matcher matcher = VARIABLE_PATTERN.matcher(request.promptContent());
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    rendered, Matcher.quoteReplacement(renderValues.get(matcher.group(1))));
        }
        matcher.appendTail(rendered);
        return new PreviewPromptVO(rendered.toString());
    }

    private PromptVersionEntity newDraft(Long promptId, Long operatorId) {
        PromptVersionEntity draft = new PromptVersionEntity();
        draft.setPromptId(promptId);
        draft.setVersionNo(versionRepo.nextVersionNo(promptId));
        draft.setStatus(PromptVersionStatus.DRAFT);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        return draft;
    }

    private PromptEntity requirePrompt(Long id) {
        return promptRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Prompt 不存在"));
    }

    private PromptVersionEntity publishedVersion(
            PromptEntity prompt, List<PromptVersionEntity> versions) {
        if (prompt.getPublishedVersionId() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Prompt 尚未创建草稿或发布版本");
        }
        return versions.stream()
                .filter(version -> version.getId().equals(prompt.getPublishedVersionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Prompt 正式版本数据不完整"));
    }

    private static Optional<PromptVersionEntity> draftVersion(
            List<PromptVersionEntity> versions) {
        return versions.stream()
                .filter(version -> version.getStatus() == PromptVersionStatus.DRAFT)
                .findFirst();
    }

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

    private static void validateTemplate(
            String promptContent, List<PromptVariableRequest> variables) {
        Set<String> declaredNames = new LinkedHashSet<>();
        for (PromptVariableRequest variable : variables) {
            if (!declaredNames.add(variable.name())) {
                throw new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "变量重复声明：" + variable.name());
            }
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(promptContent);
        Set<String> referencedNames = new LinkedHashSet<>();
        StringBuilder unmatched = new StringBuilder();
        while (matcher.find()) {
            referencedNames.add(matcher.group(1));
            matcher.appendReplacement(unmatched, "");
        }
        matcher.appendTail(unmatched);
        if (unmatched.indexOf("{{") >= 0 || unmatched.indexOf("}}") >= 0) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Prompt 中存在格式不正确的变量");
        }

        Set<String> undeclared = new LinkedHashSet<>(referencedNames);
        undeclared.removeAll(declaredNames);
        if (!undeclared.isEmpty()) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "Prompt 使用了未声明的变量：" + String.join("、", undeclared));
        }
        Set<String> unused = new LinkedHashSet<>(declaredNames);
        unused.removeAll(referencedNames);
        if (!unused.isEmpty()) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "存在未使用的变量声明：" + String.join("、", unused));
        }
    }

    private static List<PromptVariableRequest> parseVariableRequests(String variables) {
        return variables == null
                ? List.of()
                : JSON.parseArray(variables, PromptVariableRequest.class);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
