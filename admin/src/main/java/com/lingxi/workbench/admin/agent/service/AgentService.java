package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.admin.agent.model.request.AgentPageQuery;
import com.lingxi.workbench.admin.agent.model.request.CreateAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.PublishAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.SaveAgentDraftRequest;
import com.lingxi.workbench.admin.agent.model.request.UpdateAgentRequest;
import com.lingxi.workbench.admin.agent.model.vo.AgentDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentToolOptionVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageResult;

import java.util.List;

/** Agent 控制面业务服务。 */
public interface AgentService {
    /** 分页查询 Agent 列表。 */
    PageResult<AgentVO> page(AgentPageQuery query);

    /** 查看 Agent 编辑详情。 */
    AgentDetailVO detail(Long id);

    /** 查看 Agent 所有发布版本列表。 */
    List<AgentVersionVO> versions(Long id);

    /** 查询 Tool 管理中可绑定的启用工具目录。 */
    List<AgentToolOptionVO> toolOptions();

    /** 查看指定发布版本的完整定义快照。 */
    AgentVersionDetailVO versionDetail(Long id, Long versionId);

    /** 创建 Agent 稳定身份及默认策略草稿。 */
    AgentDetailVO create(CreateAgentRequest request, Long operatorId);

    /** 修改 Agent 名称和描述。 */
    AgentDetailVO update(Long id, UpdateAgentRequest request, Long operatorId);

    /** 保存 Agent 草稿配置，使用 revision 做并发保护。 */
    AgentDetailVO saveDraft(Long id, SaveAgentDraftRequest request, Long operatorId);

    /** 对当前草稿执行发布前逐项校验。 */
    AgentValidationResultVO validate(Long id);

    /** 校验并发布 Agent 当前草稿为不可变版本。 */
    AgentVersionDetailVO publish(Long id, PublishAgentRequest request, Long operatorId);

    /** 切换 Agent 当前激活的版本。 */
    void activateVersion(Long id, Long versionId, Long operatorId);

    /** 启用或停用 Agent。 */
    void changeStatus(Long id, EnableStatus status, Long operatorId);
}
