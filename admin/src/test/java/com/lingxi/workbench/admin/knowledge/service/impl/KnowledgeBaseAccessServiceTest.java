package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessSubjectType;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeAccessGrant;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeAccessRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeAccessRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeIndexVersionService;
import com.lingxi.workbench.admin.sys.department.service.SysDepartmentService;
import com.lingxi.workbench.admin.sys.role.service.SysRoleService;
import com.lingxi.workbench.admin.sys.user.service.SysUserService;
import com.lingxi.workbench.framework.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 授权全量替换必须在锁定知识库、验证主体之后发生。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAccessServiceTest {
    @Mock private KnowledgeBaseRepo bases;
    @Mock private KnowledgeAccessRepo access;
    @Mock private SysUserService users;
    @Mock private SysRoleService roles;
    @Mock private SysDepartmentService departments;
    @Mock private KnowledgeDocumentRepo documents;
    @Mock private KnowledgeConverter converter;
    @Mock private AuditService audit;
    @Mock private KnowledgeEmbeddingModelResolver embedding;
    @Mock private KnowledgeIndexVersionService indexes;
    @InjectMocks private KnowledgeBaseServiceImpl service;

    @Test
    void shouldReplaceAllGrantsAndModeTogether() {
        KnowledgeBaseEntity base = lockedBase();
        validSubjects(Set.of(12L));
        KnowledgeAccessVO result = service.saveAccess(1L, request(KnowledgeAccessMode.RESTRICTED, Set.of(12L)), 99L);
        assertThat(result.userIds()).containsExactly(12L);
        assertThat(base.getAccessMode()).isEqualTo(KnowledgeAccessMode.RESTRICTED);
        verify(access).replace(1L, List.of(new KnowledgeAccessGrant(KnowledgeAccessSubjectType.USER, 12L)));
        verify(bases).save(base);
        verifyNoInteractions(indexes);
    }

    @Test
    void shouldRevokeAllWhenRestrictedRangeBecomesEmpty() {
        lockedBase();
        validSubjects(Set.of());
        service.saveAccess(1L, request(KnowledgeAccessMode.RESTRICTED, Set.of()), 99L);
        verify(access).replace(1L, List.of());
    }

    @Test
    void shouldRejectMissingSubjectBeforeAnyWrite() {
        lockedBase();
        assertThatThrownBy(() -> service.saveAccess(1L, request(KnowledgeAccessMode.RESTRICTED, Set.of(12L)), 99L))
                .hasMessageContaining("不存在");
        verify(bases, never()).save(any());
        verifyNoInteractions(access, audit);
    }

    @Test
    void shouldRejectMixedAuthenticatedModeAndExplicitGrants() {
        lockedBase();
        validSubjects(Set.of(12L));
        assertThatThrownBy(() -> service.saveAccess(1L, request(KnowledgeAccessMode.AUTHENTICATED, Set.of(12L)), 99L))
                .hasMessageContaining("不能同时配置");
        verify(bases, never()).save(any());
        verifyNoInteractions(access, audit);
    }

    @Test
    void shouldLoadAllSubjectTypesWithoutChangingAuthorization() {
        KnowledgeBaseEntity base = new KnowledgeBaseEntity();
        base.setAccessMode(KnowledgeAccessMode.RESTRICTED);
        when(bases.findById(1L)).thenReturn(Optional.of(base));
        when(access.findByBaseId(1L)).thenReturn(List.of(new KnowledgeAccessGrant(KnowledgeAccessSubjectType.ROLE, 2L),
                new KnowledgeAccessGrant(KnowledgeAccessSubjectType.DEPARTMENT, 3L)));
        KnowledgeAccessVO result = service.access(1L);
        assertThat(result.roleIds()).containsExactly(2L);
        assertThat(result.departmentIds()).containsExactly(3L);
        assertThat(result.userIds()).isEmpty();
        verify(bases, never()).save(any());
    }

    private KnowledgeBaseEntity lockedBase() {
        KnowledgeBaseEntity base = new KnowledgeBaseEntity();
        base.setId(1L);
        when(bases.findByIdForUpdate(1L)).thenReturn(Optional.of(base));
        return base;
    }

    private void validSubjects(Set<Long> userIds) {
        when(users.existsAll(userIds)).thenReturn(true);
        when(roles.existsAll(Set.of())).thenReturn(true);
        when(departments.existsAll(Set.of())).thenReturn(true);
    }

    private static SaveKnowledgeAccessRequest request(KnowledgeAccessMode mode, Set<Long> userIds) {
        SaveKnowledgeAccessRequest request = new SaveKnowledgeAccessRequest();
        request.setMode(mode);
        request.setUserIds(userIds);
        request.setRoleIds(Set.of());
        request.setDepartmentIds(Set.of());
        return request;
    }
}
