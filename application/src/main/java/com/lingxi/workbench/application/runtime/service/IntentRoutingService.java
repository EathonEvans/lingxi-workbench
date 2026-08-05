package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;

/**
 * ApplicationVersion 绑定意图树后的运行时识别、澄清与兜底入口。
 *
 * <p>每一条用户消息到达时，{@code ApplicationWorkflowChatServiceImpl} 在
 * 启动主 Workflow 之前调用 {@link #route(ApplicationRuntimeSnapshot, String, LoginUser, ChatTurnBO, ClarificationResponseRequest)}
 * 进行意图识别。路由结果（{@link IntentRoutingResult}）决定下一步行为：</p>
 * <ul>
 *   <li>EXECUTE_WORKFLOW / EXECUTE_AGENT → 启动对应的执行流程</li>
 *   <li>FIXED_RESPONSE → 直接返回固定答复文本（不调用模型）</li>
 *   <li>CLARIFICATION → 返回澄清事件，等待用户下一轮输入</li>
 * </ul>
 */
public interface IntentRoutingService {

    /**
     * 意图路由入口。
     *
     * @param application          运行时 Application 快照，包含已锁定的版本和意图绑定配置
     * @param query                用户输入文本
     * @param loginUser            当前登录用户
     * @param turn                 当前对话轮次信息
     * @param clarificationResponse 可选的澄清响应（前端按钮选择或槽位值）
     * @return 确定性路由结果
     */
    IntentRoutingResult route(
            ApplicationRuntimeSnapshot application,
            String query,
            LoginUser loginUser,
            ChatTurnBO turn,
            ClarificationResponseRequest clarificationResponse);
}
