package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;

/**
 * ApplicationVersion 绑定意图树后的运行时识别、澄清与兜底入口。
 *
 * <p>每一条用户消息到达时，Application Chat 先准备共享上下文，再调用
 * {@link #route(ConversationContext, ClarificationResponseRequest)} 进行意图识别。
 * 路由结果（{@link IntentRoutingResult}）决定下一步行为：</p>
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
     * @param context 路由、规划和执行共享的可信对话上下文
     * @param clarificationResponse 可选的澄清响应（前端按钮选择或槽位值）
     * @return 确定性路由结果
     */
    IntentRoutingResult route(ConversationContext context, ClarificationResponseRequest clarificationResponse);
}
