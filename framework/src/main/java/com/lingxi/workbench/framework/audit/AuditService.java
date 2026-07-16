package com.lingxi.workbench.framework.audit;

import com.lingxi.workbench.framework.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 统一操作审计服务。
 *
 * <p>该服务只负责以统一格式持久化审计记录。具体操作名称、目标类型和摘要由业务层提供，
 * 避免通用审计能力反向依赖某个业务领域。</p>
 */
@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 追加一条操作审计记录。
     *
     * @param businessType 业务类型，例如 {@code SYSTEM}、{@code MODEL}
     * @param targetType   目标类型，例如 {@code USER}、{@code PROVIDER}
     * @param targetId     目标数据主键
     * @param operatorId   操作人用户主键
     * @param action       操作编码
     * @param summary      不包含密码、密钥等敏感信息的变更摘要
     */
    public void record(String businessType, String targetType, Long targetId, Long operatorId, String action, String summary) {
        auditLogMapper.insert(businessType, targetType, targetId, operatorId, action, summary);
    }
}
