package com.lingxi.workbench.admin.tool.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.model.converter.ToolConverter;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.admin.tool.model.bo.McpToolImport;
import com.lingxi.workbench.admin.tool.model.entity.ToolEntity;
import com.lingxi.workbench.admin.tool.model.request.CreateToolRequest;
import com.lingxi.workbench.admin.tool.model.request.ToolPageQuery;
import com.lingxi.workbench.admin.tool.model.request.UpdateToolRequest;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.admin.tool.repo.ToolRepo;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.admin.mcp.service.McpServerService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Tool 控制面业务实现。
 */
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final ToolRepo toolRepo;
    private final ToolConverter toolConverter;
    private final ToolParameterSchemaFactory parameterSchemaFactory;
    private final McpServerService mcpServerService;
    private final AuditService auditService;

    /**
     * 分页查询 Tool 定义。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ToolVO> page(ToolPageQuery query) {
        return toolRepo.findPage(query).map(toolConverter::toVO);
    }

    /**
     * 查询 Tool 完整定义。
     */
    @Override
    @Transactional(readOnly = true)
    public ToolVO detail(Long id) {
        return toolConverter.toVO(requireTool(id));
    }

    /**
     * 查询 Agent 当前允许新增绑定的启用 Tool。
     */
    @Override
    @Transactional(readOnly = true)
    public List<ToolOptionVO> activeOptions() {
        return toolRepo.findAllActive().stream().map(toolConverter::toOptionVO).toList();
    }

    /**
     * 批量返回处于启用状态的 Tool 编码。
     */
    @Override
    @Transactional(readOnly = true)
    public Set<String> findActiveCodes(List<String> codes) {
        return toolRepo.findActiveCodes(codes);
    }

    /**
     * 创建 Tool 稳定定义；code 创建后不允许修改。
     */
    @Override
    @Transactional
    public ToolVO create(CreateToolRequest request, Long operatorId) {
        if (request.getSourceType() != ToolSourceType.LOCAL_FUNCTION) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "MCP Tool 必须从 MCP Server 的工具发现结果导入");
        }
        String code = request.getCode().trim().toLowerCase();
        if (toolRepo.existsByCode(code)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool 编码已存在");
        }
        ToolEntity entity = new ToolEntity();
        entity.setName(request.getName().trim());
        entity.setCode(code);
        entity.setDescription(request.getDescription().trim());
        entity.setSourceType(request.getSourceType());
        entity.setParameterSchema(parameterSchemaFactory.build(request.getParameters()));
        entity.setStatus(request.getStatus());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        toolRepo.save(entity);
        auditService.record("TOOL", "DEFINITION", entity.getId(), operatorId, "TOOL_CREATE", "Tool=" + code);
        return toolConverter.toVO(entity);
    }

    /**
     * 导入远端 MCP Tool。Schema 只接受服务端重新发现的结果，初始状态固定为停用。
     */
    @Override
    @Transactional
    public ToolVO importMcpTool(McpToolImport source, Long operatorId) {
        if (toolRepo.existsByCode(source.getCode())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool 编码已存在");
        }
        if (toolRepo.existsMcpBinding(source.getMcpServerId(), source.getRemoteToolName())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该 MCP 工具已经导入");
        }
        parameterSchemaFactory.parse(source.getParameterSchema());

        ToolEntity entity = new ToolEntity();
        entity.setName(source.getName());
        entity.setCode(source.getCode());
        entity.setDescription(source.getDescription());
        entity.setSourceType(ToolSourceType.MCP);
        entity.setMcpServerId(source.getMcpServerId());
        entity.setRemoteToolName(source.getRemoteToolName());
        entity.setParameterSchema(source.getParameterSchema());
        entity.setStatus(EnableStatus.DISABLED);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        toolRepo.save(entity);
        auditService.record("TOOL", "DEFINITION", entity.getId(), operatorId, "MCP_TOOL_IMPORT", "Tool=" + source.getCode() + ", remoteTool=" + source.getRemoteToolName());
        return toolConverter.toVO(entity);
    }

    /**
     * 修改模型可见描述和参数契约，不允许修改稳定编码与来源。
     */
    @Override
    @Transactional
    public ToolVO update(Long id, UpdateToolRequest request, Long operatorId) {
        ToolEntity entity = requireTool(id);
        if (entity.getSourceType() == ToolSourceType.MCP && !parameterSchemaFactory.parse(entity.getParameterSchema()).equals(request.getParameters())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "MCP Tool 参数由远端 Schema 决定，不能手动修改");
        }
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription().trim());
        if (entity.getSourceType() == ToolSourceType.LOCAL_FUNCTION) {
            entity.setParameterSchema(parameterSchemaFactory.build(request.getParameters()));
        }
        entity.setUpdatedBy(operatorId);
        toolRepo.save(entity);
        auditService.record("TOOL", "DEFINITION", id, operatorId, "TOOL_UPDATE", "Tool=" + entity.getCode());
        return toolConverter.toVO(entity);
    }

    /**
     * 启用或停用 Tool；停用后 Agent 发布校验会立即拒绝该绑定。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        ToolEntity entity = requireTool(id);
        if (status == EnableStatus.ACTIVE && entity.getSourceType() == ToolSourceType.MCP) {
            mcpServerService.validateRuntimeAvailable(entity.getMcpServerId());
        }
        entity.setStatus(status);
        entity.setUpdatedBy(operatorId);
        toolRepo.save(entity);
        auditService.record("TOOL", "DEFINITION", id, operatorId, "TOOL_STATUS_CHANGE", "状态=" + status);
    }

    private ToolEntity requireTool(Long id) {
        return toolRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Tool 不存在"));
    }
}
