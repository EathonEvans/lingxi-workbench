package com.lingxi.workbench.admin.knowledge.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeBaseService;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeIndexVersionService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessSubjectType;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeAccessGrant;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeAccessRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessOptionVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeAccessRepo;
import com.lingxi.workbench.admin.sys.department.model.vo.SysDepartmentVO;
import com.lingxi.workbench.admin.sys.department.service.SysDepartmentService;
import com.lingxi.workbench.admin.sys.role.service.SysRoleService;
import com.lingxi.workbench.admin.sys.user.service.SysUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 知识库管理业务实现。
 *
 * <p>负责知识库的增删改查，包括编码唯一性校验、Embedding 模型绑定校验
 * 以及有文档时禁止更换模型和删除的保护逻辑。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeBaseRepo baseRepo;
    private final KnowledgeAccessRepo accessRepo;
    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysDepartmentService departmentService;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeConverter converter;
    private final AuditService auditService;
    private final KnowledgeEmbeddingModelResolver embeddingModelResolver;
    private final KnowledgeIndexVersionService knowledgeIndexVersionService;

    /**
     * 分页查询知识库列表，按关键字和状态过滤，按更新时间降序排列。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query) {
        return baseRepo.findPage(query).map(converter::toBaseVO);
    }

    /**
     * 按主键加载知识库详情，不存在时抛出业务异常。
     */
    @Override
    @Transactional(readOnly = true)
    public KnowledgeBaseVO detail(Long id) {
        return converter.toBaseVO(requireBase(id));
    }

    /**
     * 新增或修改知识库。
     *
     * <p>新增和修改统一入口，ID 为空时新增否则修改。
     * 修改时若已有文档，不允许更换 Embedding 模型，以免索引契约不兼容。</p>
     */
    @Override
    @Transactional
    public KnowledgeBaseVO save(Long id, SaveKnowledgeBaseRequest request, Long operatorId) {
        // 统一转为小写编码做唯一性校验
        String code = request.getCode().trim().toLowerCase();
        if (baseRepo.existsByCode(code, id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库编码已存在");
        }
        embeddingModelResolver.validate(request.getEmbeddingModelInstanceId());
        KnowledgeBaseEntity entity = Objects.isNull(id) ? new KnowledgeBaseEntity() : baseRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
        if (Objects.nonNull(id)
                && !Objects.equals(entity.getEmbeddingModelInstanceId(), request.getEmbeddingModelInstanceId())
                && documentRepo.existsByBaseId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "知识库已有文档，不能直接更换Embedding模型，请新建知识库或先清空文档");
        }
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setDescription(CharSequenceUtil.isBlank(request.getDescription()) ? null : request.getDescription().trim());
        entity.setEmbeddingModelInstanceId(request.getEmbeddingModelInstanceId());
        entity.setStatus(request.getStatus());
        entity.setUpdatedBy(operatorId);
        if (Objects.isNull(id)) {
            entity.setCreatedBy(operatorId);
            entity.setAccessMode(KnowledgeAccessMode.RESTRICTED);
        }
        baseRepo.save(entity);
        auditService.record("KNOWLEDGE", "BASE", entity.getId(), operatorId,
                Objects.isNull(id) ? "KNOWLEDGE_CREATE" : "KNOWLEDGE_UPDATE", "知识库=" + code);
        return converter.toBaseVO(entity);
    }

    /**
     * 删除无文档的知识库。
     *
     * <p>知识库内仍有文档时拒绝删除，防止遗留孤儿文档和向量数据。</p>
     */
    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        KnowledgeBaseEntity entity = baseRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
        if (documentRepo.existsByBaseId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库中仍有文档，不能删除");
        }
        if (knowledgeIndexVersionService.hasPublishedVersion(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库已有发布索引版本，不能删除");
        }
        accessRepo.replace(id, List.of());
        baseRepo.delete(id);
        auditService.record("KNOWLEDGE", "BASE", id, operatorId, "KNOWLEDGE_DELETE", "知识库=" + entity.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeAccessVO access(Long id) {
        KnowledgeBaseEntity base = requireBase(id);
        List<KnowledgeAccessGrant> grants = accessRepo.findByBaseId(id);
        return new KnowledgeAccessVO(base.getAccessMode(), subjectIds(grants, KnowledgeAccessSubjectType.USER),
                subjectIds(grants, KnowledgeAccessSubjectType.ROLE), subjectIds(grants, KnowledgeAccessSubjectType.DEPARTMENT));
    }

    @Override
    @Transactional
    public KnowledgeAccessVO saveAccess(Long id, SaveKnowledgeAccessRequest request, Long operatorId) {
        KnowledgeBaseEntity base = baseRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
        if (!userService.existsAll(request.getUserIds()) || !roleService.existsAll(request.getRoleIds())
                || !departmentService.existsAll(request.getDepartmentIds())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "授权范围包含不存在的用户、角色或部门");
        }
        List<KnowledgeAccessGrant> grants = new ArrayList<>();
        request.getUserIds().forEach(subjectId -> grants.add(new KnowledgeAccessGrant(KnowledgeAccessSubjectType.USER, subjectId)));
        request.getRoleIds().forEach(subjectId -> grants.add(new KnowledgeAccessGrant(KnowledgeAccessSubjectType.ROLE, subjectId)));
        request.getDepartmentIds().forEach(subjectId -> grants.add(new KnowledgeAccessGrant(KnowledgeAccessSubjectType.DEPARTMENT, subjectId)));
        if (request.getMode() == KnowledgeAccessMode.AUTHENTICATED && !grants.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "全部应用用户模式不能同时配置指定授权主体");
        }
        // 与普通资料保存共用知识库行锁，授权主体全量替换和访问模式必须一起生效。
        base.setAccessMode(request.getMode());
        base.setUpdatedBy(operatorId);
        baseRepo.save(base);
        accessRepo.replace(id, grants);
        auditService.record("KNOWLEDGE", "BASE", id, operatorId, "KNOWLEDGE_ACCESS_UPDATE",
                "读取范围=" + request.getMode().getCode() + "，授权主体数=" + grants.size());
        return new KnowledgeAccessVO(request.getMode(), Set.copyOf(request.getUserIds()),
                Set.copyOf(request.getRoleIds()), Set.copyOf(request.getDepartmentIds()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeAccessOptionVO> userOptions(SysUserPageQuery query) {
        return userService.page(query).map(user -> new KnowledgeAccessOptionVO(user.getId(), user.getDisplayName() + "（" + user.getUsername() + "）"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeAccessOptionVO> roleOptions() {
        return roleService.list().stream().map(role -> new KnowledgeAccessOptionVO(role.getId(), role.getName())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeAccessOptionVO> departmentOptions() {
        List<KnowledgeAccessOptionVO> options = new ArrayList<>();
        appendDepartments(departmentService.listTree(), "", options);
        return options;
    }

    private static void appendDepartments(List<SysDepartmentVO> departments, String parentPath, List<KnowledgeAccessOptionVO> options) {
        for (SysDepartmentVO department : departments) {
            String label = parentPath + department.getName();
            options.add(new KnowledgeAccessOptionVO(department.getId(), label));
            appendDepartments(department.getChildren(), label + " / ", options);
        }
    }

    private static Set<Long> subjectIds(List<KnowledgeAccessGrant> grants, KnowledgeAccessSubjectType type) {
        return grants.stream().filter(grant -> grant.subjectType() == type)
                .map(KnowledgeAccessGrant::subjectId).collect(Collectors.toSet());
    }

    /** 按 ID 加载知识库，不存在时抛出业务异常。 */
    private KnowledgeBaseEntity requireBase(Long id) {
        return baseRepo.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
    }
}
