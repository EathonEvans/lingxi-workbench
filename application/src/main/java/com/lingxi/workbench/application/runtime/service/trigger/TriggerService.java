package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.service.trigger.model.request.CreateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.request.UpdateTriggerRequest;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerCapabilitiesVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerFireVO;
import com.lingxi.workbench.application.runtime.service.trigger.model.vo.TriggerVO;
import com.lingxi.workbench.framework.convention.PageQuery;

import java.util.List;
import com.lingxi.workbench.framework.convention.PageResult;

/** 定时触发器业务：创建、查询、调度控制与运行历史。 */
public interface TriggerService {

    /**
     * 创建触发器：校验 cron、数量上限与冻结能力目录中的唯一能力，并创建专属会话。
     */
    TriggerVO create(Long userId, CreateTriggerRequest request);

    /**
     * 列出应用当前发布版本中可绑定的 AGENT/WORKFLOW 能力，并返回冻结绑定所需的版本标识。
     */
    TriggerCapabilitiesVO listCapabilities(Long userId, String applicationCode);

    /**
     * 分页查询本人触发器。
     */
    PageResult<TriggerVO> page(Long userId, PageQuery query);

    /**
     * 更新可编辑字段（名称、目标、调度、静默与白名单），并重算下次触发时间。
     */
    void update(Long userId, Long triggerId, UpdateTriggerRequest request);

    /**
     * 暂停调度。
     */
    void pause(Long userId, Long triggerId);

    /**
     * 恢复调度：清零连续失败计数并重算下次触发时间。
     */
    void resume(Long userId, Long triggerId);

    /**
     * 逻辑删除触发器。
     */
    void delete(Long userId, Long triggerId);

    /**
     * 分页查询触发器的运行历史。
     */
    PageResult<TriggerFireVO> firePage(Long userId, Long triggerId, PageQuery query);
}
