package com.lingxi.workbench.admin.mcp.controller;

import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.service.McpServerService;
import com.lingxi.workbench.admin.mcp.service.McpToolImportService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerControllerTest {

    private final McpServerService service = mock(McpServerService.class);
    private final McpToolImportService importService = mock(McpToolImportService.class);
    private final McpServerController controller = new McpServerController(service, importService);

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldUseCurrentOperatorWhenDiscoveringTools() {
        UserContext.set(new LoginUser(7L, "tester", "测试用户", null));
        McpServerInspectionVO inspection = McpServerInspectionVO.builder().message("连接成功").build();
        when(service.discoverTools(10L, 7L)).thenReturn(inspection);

        ApiResponse<McpServerInspectionVO> response = controller.discoverTools(10L);

        assertEquals(200, response.code());
        assertEquals(inspection, response.data());
        verify(service).discoverTools(10L, 7L);
    }
}
