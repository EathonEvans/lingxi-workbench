package com.lingxi.workbench.admin.tool.service;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.model.request.CreateToolRequest;
import com.lingxi.workbench.admin.tool.model.request.ToolPageQuery;
import com.lingxi.workbench.admin.tool.model.request.UpdateToolRequest;
import com.lingxi.workbench.admin.tool.model.bo.McpToolImport;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.admin.tool.model.vo.ToolVO;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;
import java.util.Set;

/** Tool 控制面业务服务。 */
public interface ToolService {

    PageResult<ToolVO> page(ToolPageQuery query);

    ToolVO detail(Long id);

    List<ToolOptionVO> activeOptions();

    Set<String> findActiveCodes(List<String> codes);

    ToolVO create(CreateToolRequest request, Long operatorId);

    ToolVO importMcpTool(McpToolImport source, Long operatorId);

    ToolVO update(Long id, UpdateToolRequest request, Long operatorId);

    void changeStatus(Long id, EnableStatus status, Long operatorId);
}
