package com.lingxi.workbench.admin.mcp.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.model.bo.McpConnectionConfig;
import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.admin.mcp.model.converter.McpServerConverter;
import com.lingxi.workbench.admin.mcp.model.entity.McpServerEntity;
import com.lingxi.workbench.admin.mcp.model.request.CreateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.request.McpServerPageQuery;
import com.lingxi.workbench.admin.mcp.model.request.UpdateMcpServerRequest;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerVO;
import com.lingxi.workbench.admin.mcp.repo.McpServerRepo;
import com.lingxi.workbench.admin.mcp.service.McpConnectionClient;
import com.lingxi.workbench.admin.mcp.service.McpServerService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.McpClientSecurityException;
import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * MCP Server 控制面业务实现。
 */
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerRepo mcpServerRepo;
    private final McpServerConverter mcpServerConverter;
    private final McpConnectionClient mcpConnectionClient;
    private final McpServerAddressPolicy addressPolicy;
    private final SecretCipher secretCipher;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<McpServerVO> page(McpServerPageQuery query) {
        return mcpServerRepo.findPage(query).map(mcpServerConverter::toVO);
    }

    @Override
    @Transactional(readOnly = true)
    public McpServerVO detail(Long id) {
        return mcpServerConverter.toVO(requireServer(id));
    }

    /**
     * 新建连接配置。MCP Server 必须先以停用状态保存并通过真实连接测试，才能启用。
     */
    @Override
    @Transactional
    public McpServerVO create(CreateMcpServerRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        if (mcpServerRepo.existsByCode(code)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "MCP Server 编码已存在");
        }
        validateConnectionConfiguration(request.getBaseUrl(), request.getEndpoint());

        McpServerEntity entity = new McpServerEntity();
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setDescription(trimNullable(request.getDescription()));
        entity.setTransportType(request.getTransportType());
        entity.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        entity.setEndpoint(request.getEndpoint().trim());
        entity.setAuthenticationType(request.getAuthenticationType());
        applyCreatedCredential(entity, request.getBearerToken());
        entity.setRequestTimeoutSeconds(request.getRequestTimeoutSeconds());
        entity.setStatus(EnableStatus.DISABLED);
        resetTestResult(entity);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        mcpServerRepo.save(entity);
        auditService.record("MCP", "SERVER", entity.getId(), operatorId, "MCP_SERVER_CREATE", "MCP Server=" + code);
        return mcpServerConverter.toVO(entity);
    }

    /**
     * 修改连接配置。启用状态下只允许修改展示信息，避免已启用连接在未测试时发生漂移。
     */
    @Override
    @Transactional
    public McpServerVO update(Long id, UpdateMcpServerRequest request, Long operatorId) {
        McpServerEntity entity = requireServer(id);
        validateConnectionConfiguration(request.getBaseUrl(), request.getEndpoint());
        boolean connectionChanged = hasConnectionChange(entity, request);
        if (connectionChanged && entity.getStatus() == EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先停用 MCP Server，再修改连接配置");
        }

        entity.setName(request.getName().trim());
        entity.setDescription(trimNullable(request.getDescription()));
        entity.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        entity.setEndpoint(request.getEndpoint().trim());
        applyUpdatedCredential(entity, request.getAuthenticationType(), request.getBearerToken());
        entity.setRequestTimeoutSeconds(request.getRequestTimeoutSeconds());
        if (connectionChanged) {
            resetTestResult(entity);
        }
        entity.setUpdatedBy(operatorId);
        mcpServerRepo.save(entity);
        auditService.record("MCP", "SERVER", id, operatorId, "MCP_SERVER_UPDATE", "MCP Server=" + entity.getCode());
        return mcpServerConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        McpServerEntity entity = requireServer(id);
        if (status == EnableStatus.ACTIVE && entity.getLastTestStatus() != McpConnectionTestStatus.SUCCESS) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "MCP Server 最近一次连接测试未成功，不能启用");
        }
        entity.setStatus(status);
        entity.setUpdatedBy(operatorId);
        mcpServerRepo.save(entity);
        auditService.record("MCP", "SERVER", id, operatorId, "MCP_SERVER_STATUS_CHANGE", "状态=" + status.getCode());
    }

    /**
     * 执行真实 MCP 初始化和 tools/list，并持久化最近测试状态。
     *
     * <p>外部调用不放入数据库事务，避免网络等待占用数据库连接和长事务。</p>
     */
    @Override
    public McpServerInspectionVO testConnection(Long id, Long operatorId) {
        McpServerEntity entity = requireServer(id);
        McpInspectionResult result = inspect(entity);
        recordInspection(entity, result, operatorId);
        return mcpServerConverter.toInspectionVO(result);
    }

    /**
     * 发现远端工具。失败时同时记录测试状态，并以业务异常返回明确原因。
     */
    @Override
    public McpServerInspectionVO discoverTools(Long id, Long operatorId) {
        McpServerEntity entity = requireServer(id);
        McpInspectionResult result = inspect(entity);
        recordInspection(entity, result, operatorId);
        if (!result.isSuccess()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, result.getMessage());
        }
        return mcpServerConverter.toInspectionVO(result);
    }

    /**
     * 导入前重新从服务端发现目标工具，禁止信任前端回传的描述和 Schema。
     */
    @Override
    public McpDiscoveredTool discoverTool(Long id, String remoteToolName, Long operatorId) {
        McpServerEntity entity = requireServer(id);
        McpInspectionResult result = inspect(entity);
        recordInspection(entity, result, operatorId);
        if (!result.isSuccess()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, result.getMessage());
        }
        return result.getTools().stream()
                .filter(tool -> tool.getName().equals(remoteToolName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "MCP Server 当前未暴露该工具：" + remoteToolName));
    }

    /**
     * MCP Tool 启用前校验其 Server 主体仍可用于运行时调用。
     */
    @Override
    @Transactional(readOnly = true)
    public void validateRuntimeAvailable(Long id) {
        McpServerEntity entity = requireServer(id);
        if (entity.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "请先启用该 Tool 绑定的 MCP Server");
        }
        if (entity.getLastTestStatus() != McpConnectionTestStatus.SUCCESS) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "MCP Server 最近一次连接测试未成功");
        }
    }

    private McpInspectionResult inspect(McpServerEntity entity) {
        String bearerToken = null;
        if (entity.getAuthenticationType() == McpAuthenticationType.BEARER_TOKEN) {
            try {
                bearerToken = secretCipher.decrypt(entity.getSecretCiphertext(), entity.getSecretIv());
            } catch (IllegalStateException exception) {
                return McpInspectionResult.failed(0, "CREDENTIAL_ERROR", "MCP Bearer Token 解密失败，请检查服务端主密钥配置");
            }
        }
        McpConnectionConfig config = McpConnectionConfig.builder()
                .baseUrl(entity.getBaseUrl())
                .endpoint(entity.getEndpoint())
                .bearerToken(bearerToken)
                .requestTimeoutSeconds(entity.getRequestTimeoutSeconds())
                .build();
        return mcpConnectionClient.inspect(config);
    }

    private void recordInspection(McpServerEntity entity, McpInspectionResult result, Long operatorId) {
        entity.setLastTestStatus(result.getStatus());
        entity.setLastTestLatencyMs(result.getLatencyMs());
        entity.setLastTestErrorCode(result.getErrorCode());
        entity.setLastTestMessage(result.getMessage());
        entity.setLastTestAt(OffsetDateTime.now());
        entity.setUpdatedBy(operatorId);
        mcpServerRepo.save(entity);
        auditService.record("MCP", "SERVER", entity.getId(), operatorId, "MCP_SERVER_CONNECTION_TEST", "测试状态=" + result.getStatus().getCode() + ", 耗时=" + result.getLatencyMs() + "ms");
    }

    private void applyCreatedCredential(McpServerEntity entity, String bearerToken) {
        if (entity.getAuthenticationType() == McpAuthenticationType.NONE) {
            clearCredential(entity);
            return;
        }
        if (CharSequenceUtil.isBlank(bearerToken)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Bearer Token 认证方式必须填写 Token");
        }
        encryptCredential(entity, bearerToken);
    }

    private void applyUpdatedCredential(McpServerEntity entity, McpAuthenticationType authenticationType, String bearerToken) {
        entity.setAuthenticationType(authenticationType);
        if (authenticationType == McpAuthenticationType.NONE) {
            clearCredential(entity);
            return;
        }
        if (CharSequenceUtil.isNotBlank(bearerToken)) {
            encryptCredential(entity, bearerToken);
            return;
        }
        if (CharSequenceUtil.isBlank(entity.getSecretCiphertext())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Bearer Token 认证方式必须填写 Token");
        }
    }

    private void encryptCredential(McpServerEntity entity, String bearerToken) {
        SecretCipher.EncryptedSecret encrypted = secretCipher.encrypt(bearerToken.trim());
        entity.setSecretCiphertext(encrypted.ciphertext());
        entity.setSecretIv(encrypted.iv());
        entity.setSecretHint(secretHint(bearerToken));
    }

    private static void clearCredential(McpServerEntity entity) {
        entity.setSecretCiphertext(null);
        entity.setSecretIv(null);
        entity.setSecretHint(null);
    }

    private static boolean hasConnectionChange(McpServerEntity entity, UpdateMcpServerRequest request) {
        return !entity.getBaseUrl().equals(normalizeBaseUrl(request.getBaseUrl()))
                || !entity.getEndpoint().equals(request.getEndpoint().trim())
                || entity.getAuthenticationType() != request.getAuthenticationType()
                || !entity.getRequestTimeoutSeconds().equals(request.getRequestTimeoutSeconds())
                || CharSequenceUtil.isNotBlank(request.getBearerToken());
    }

    private static void resetTestResult(McpServerEntity entity) {
        entity.setLastTestStatus(McpConnectionTestStatus.UNTESTED);
        entity.setLastTestLatencyMs(null);
        entity.setLastTestErrorCode(null);
        entity.setLastTestMessage("尚未测试连接");
        entity.setLastTestAt(null);
    }

    private McpServerEntity requireServer(Long id) {
        return mcpServerRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "MCP Server 不存在"));
    }

    private void validateConnectionConfiguration(String baseUrl, String endpoint) {
        try {
            addressPolicy.validateConfiguration(baseUrl, endpoint);
        } catch (McpClientSecurityException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, exception.getMessage());
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private static String trimNullable(String value) {
        return Optional.ofNullable(value).map(String::trim).orElse(null);
    }

    private static String secretHint(String value) {
        String secret = value.trim();
        return "••••" + secret.substring(Math.max(0, secret.length() - 4));
    }
}
