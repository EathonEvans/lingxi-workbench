package com.lingxi.workbench.admin.tool.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.admin.tool.model.bo.McpToolImport;
import com.lingxi.workbench.admin.tool.model.converter.ToolConverter;
import com.lingxi.workbench.admin.tool.model.entity.ToolEntity;
import com.lingxi.workbench.admin.tool.model.request.CreateToolRequest;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.admin.tool.repo.ToolRepo;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP Tool 导入规则回归测试。
 */
class ToolServiceImplTest {

    private final InMemoryToolRepo toolRepo = new InMemoryToolRepo();
    private final ToolParameterSchemaFactory schemaFactory = new ToolParameterSchemaFactory();
    private final ToolServiceImpl service = new ToolServiceImpl(toolRepo, new ToolConverter(schemaFactory), schemaFactory, null, new NoopAuditService());

    @Test
    void shouldImportMcpToolAsDisabledStableDefinition() {
        McpToolImport source = importSource();

        ToolVO tool = service.importMcpTool(source, 7L);

        ToolEntity saved = toolRepo.findById(Long.valueOf(tool.getId())).orElseThrow();
        assertEquals(ToolSourceType.MCP, saved.getSourceType());
        assertEquals(10L, saved.getMcpServerId());
        assertEquals("system_calculator", saved.getRemoteToolName());
        assertEquals(EnableStatus.DISABLED, saved.getStatus());
        assertEquals(3, tool.getParameters().size());
    }

    @Test
    void shouldRejectDuplicatedMcpBinding() {
        service.importMcpTool(importSource(), 7L);

        McpToolImport duplicated = McpToolImport.builder()
                .mcpServerId(10L)
                .remoteToolName("system_calculator")
                .code("another_calculator")
                .name("另一个计算器")
                .description("重复绑定")
                .parameterSchema(importSource().getParameterSchema())
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.importMcpTool(duplicated, 7L));

        assertEquals("该 MCP 工具已经导入", exception.getMessage());
    }

    @Test
    void shouldRequireApprovalForHighRiskTool() {
        CreateToolRequest request = new CreateToolRequest();
        request.setName("发送邮件");
        request.setCode("send_mail");
        request.setDescription("向外部邮箱发送内容");
        request.setSourceType(ToolSourceType.LOCAL_FUNCTION);
        request.setParameters(List.of());
        request.setRiskLevel(ToolRiskLevel.HIGH);
        request.setApprovalRequired(false);
        request.setApprovalTtlSeconds(1800);
        request.setResourceScope("外部邮箱");
        request.setStatus(EnableStatus.DISABLED);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request, 7L));

        assertEquals("高风险和关键操作必须启用人工批准", exception.getMessage());
    }

    private static McpToolImport importSource() {
        return McpToolImport.builder()
                .mcpServerId(10L)
                .remoteToolName("system_calculator")
                .code("local_mcp_calculator")
                .name("MCP 计算器")
                .description("通过本地 MCP Server 执行基础计算")
                .parameterSchema(JSONObject.of(
                        "type", "object",
                        "properties", JSONObject.of(
                                "leftOperand", JSONObject.of("type", "number", "description", "左操作数"),
                                "operator", JSONObject.of("type", "string", "description", "运算符"),
                                "rightOperand", JSONObject.of("type", "number", "description", "右操作数")),
                        "required", List.of("leftOperand", "operator", "rightOperand"),
                        "additionalProperties", false))
                .build();
    }

    private static final class InMemoryToolRepo extends ToolRepo {

        private final List<ToolEntity> tools = new ArrayList<>();

        private InMemoryToolRepo() {
            super(null);
        }

        @Override
        public Optional<ToolEntity> findById(Long id) {
            return tools.stream().filter(tool -> tool.getId().equals(id)).findFirst();
        }

        @Override
        public boolean existsByCode(String code) {
            return tools.stream().anyMatch(tool -> tool.getCode().equals(code));
        }

        @Override
        public boolean existsMcpBinding(Long mcpServerId, String remoteToolName) {
            return tools.stream().anyMatch(tool -> mcpServerId.equals(tool.getMcpServerId()) && remoteToolName.equals(tool.getRemoteToolName()));
        }

        @Override
        public void save(ToolEntity entity) {
            entity.setId((long) tools.size() + 1);
            tools.add(entity);
        }
    }

    private static final class NoopAuditService extends AuditService {

        private NoopAuditService() {
            super(null);
        }

        @Override
        public void record(String businessType, String targetType, Long targetId, Long operatorId, String action, String summary) {
        }
    }
}
