package com.lingxi.workbench.chat.service;

import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationMemorySnapshot;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;

import java.util.List;
import java.util.Optional;

/**
 * 会话记忆业务。
 */
public interface ConversationMemoryService {

    /**
     * 一次性加载共享上下文需要的摘要和窗口记忆，并校验会话归属。
     *
     * @param userId 当前登录用户主键
     * @param turn 当前已落库的对话轮次
     * @param budget 共享上下文历史预算
     * @return 摘要覆盖范围之后的会话记忆快照
     */
    ConversationMemorySnapshot loadContextMemory(Long userId, ChatTurnBO turn, ContextBudget budget);

    /**
     * 加载上一版摘要之后最早的一批完整问答，达到触发轮数时才返回摘要输入。
     *
     * @param userId       当前用户主键
     * @param turn         已完成的当前对话轮次
     * @param trigger      新增轮数、Token 阈值和输入上限
     * @return 可生成下一版摘要的滚动输入，未达到阈值时为空
     */
    Optional<ConversationSummarySource> loadSummarySource(Long userId, ChatTurnBO turn, ConversationSummaryTrigger trigger);

    /**
     * 获取当前轮之前可注入模型的窗口记忆。
     *
     * <p>只选择已经完成的“用户消息 + Assistant 消息”完整轮次，随后同时按照最大轮数和
     * Token 预算从近到远裁剪，返回结果再恢复为消息产生时间正序。</p>
     *
     * @param userId           当前登录用户主键，用于校验会话归属
     * @param conversationId   会话主键
     * @param beforeSequenceNo 当前用户消息序号，只查询该序号之前的历史消息
     * @return 可直接注入模型上下文的历史消息，按产生时间正序排列
     */
    List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo);

    /**
     * 使用 AgentVersion 冻结的窗口限制读取会话记忆。
     */
    List<ChatMemoryMessageBO> getWindowMemory(Long userId, Long conversationId, Integer beforeSequenceNo, int maxRounds, int maxTokens);

    /**
     * 使用默认窗口限制裁剪已经加载的完整问答轮次，不再访问数据库。
     */
    List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages);

    /**
     * 按指定限制裁剪已经加载的完整问答轮次，不再访问数据库。
     */
    List<ChatMemoryMessageBO> selectWindow(List<ChatMemoryMessageBO> messages, int maxRounds, int maxTokens);
}
