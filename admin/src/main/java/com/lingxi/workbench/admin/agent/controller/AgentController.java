package com.lingxi.workbench.admin.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.agent.model.request.AgentPageQuery;
import com.lingxi.workbench.admin.agent.model.request.CreateAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.PublishAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.SaveAgentDraftRequest;
import com.lingxi.workbench.admin.agent.model.request.UpdateAgentRequest;
import com.lingxi.workbench.admin.agent.model.vo.AgentDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionVO;
import com.lingxi.workbench.admin.agent.service.AgentService;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端 Agent 配置与版本管理接口。 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    /** 分页查询 Agent 列表，支持关键字、类型和状态过滤。 */
    @GetMapping("/list")
    @SaCheckPermission("agent:list")
    public ApiResponse<PageResult<AgentVO>> list(@Valid AgentPageQuery query) {
        return ApiResponse.success(agentService.page(query));
    }

    /** 查看 Agent 编辑详情，包含草稿、知识库绑定和发布版本摘要。 */
    @GetMapping("/detail/{id}")
    @SaCheckPermission("agent:list")
    public ApiResponse<AgentDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(agentService.detail(id));
    }

    /** 查看 Agent 所有历史发布版本列表。 */
    @GetMapping("/versions/{id}")
    @SaCheckPermission("agent:list")
    public ApiResponse<List<AgentVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(agentService.versions(id));
    }

    /** 查看指定发布版本的完整定义快照详情。 */
    @GetMapping("/version-detail/{id}/{versionId}")
    @SaCheckPermission("agent:list")
    public ApiResponse<AgentVersionDetailVO> versionDetail(
            @PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(agentService.versionDetail(id, versionId));
    }

    /** 创建 Agent 稳定身份及默认策略草稿。 */
    @PostMapping("/create")
    @SaCheckPermission("agent:manage")
    public ApiResponse<AgentDetailVO> create(
            @Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentService.create(request, UserContext.userId()));
    }

    /** 修改 Agent 名称和描述元信息。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("agent:manage")
    public ApiResponse<AgentDetailVO> update(
            @PathVariable Long id, @Valid @RequestBody UpdateAgentRequest request) {
        return ApiResponse.success(agentService.update(id, request, UserContext.userId()));
    }

    /** 保存 Agent 草稿配置，使用 revision 实现并发编辑保护。 */
    @PostMapping("/save-draft/{id}")
    @SaCheckPermission("agent:manage")
    public ApiResponse<AgentDetailVO> saveDraft(
            @PathVariable Long id, @Valid @RequestBody SaveAgentDraftRequest request) {
        return ApiResponse.success(
                agentService.saveDraft(id, request, UserContext.userId()));
    }

    /** 对当前草稿执行发布前校验，返回逐项检查结果。 */
    @PostMapping("/validate/{id}")
    @SaCheckPermission("agent:manage")
    public ApiResponse<AgentValidationResultVO> validate(@PathVariable Long id) {
        return ApiResponse.success(agentService.validate(id));
    }

    /** 校验并发布 Agent 当前草稿，生成不可变版本快照。 */
    @PostMapping("/publish/{id}")
    @SaCheckPermission("agent:publish")
    public ApiResponse<AgentVersionDetailVO> publish(
            @PathVariable Long id, @Valid @RequestBody PublishAgentRequest request) {
        return ApiResponse.success(
                agentService.publish(id, request, UserContext.userId()));
    }

    /** 切换 Actived Agent 当前生效的发布版本。 */
    @PostMapping("/activate-version/{id}/{versionId}")
    @SaCheckPermission("agent:publish")
    public ApiResponse<Void> activateVersion(
            @PathVariable Long id, @PathVariable Long versionId) {
        agentService.activateVersion(id, versionId, UserContext.userId());
        return ApiResponse.success();
    }

    /** 启用或停用 Agent。 */
    @PostMapping("/change-status/{id}")
    @SaCheckPermission("agent:manage")
    public ApiResponse<Void> changeStatus(
            @PathVariable Long id, @RequestParam EnableStatus status) {
        agentService.changeStatus(id, status, UserContext.userId());
        return ApiResponse.success();
    }
}
