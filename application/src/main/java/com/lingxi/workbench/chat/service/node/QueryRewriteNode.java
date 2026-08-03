package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 将依赖上下文的用户追问改写为可独立执行向量检索的问题。
 *
 * <p>节点只产出检索问题，不修改用户原始问题。后续 Prompt 构建和最终回答仍然消费
 * {@code user.query}，避免改写模型改变用户真实意图。</p>
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class QueryRewriteNode extends AbstractChatProcessNode {

    private final ChatCompletionGateway chatCompletionGateway;
    private final PromptTemplateRenderer promptTemplateRenderer;

    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        String originalQuery = context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY);
        QueryRewriteConfig config = context.getQueryRewriteConfig();
        if (config == null || !config.isEnabled()) {
            // 未开启则使用原query
            writeRetrievalQuery(context, originalQuery);
            return ChatProcessResult.CONTINUE;
        }

        try {
            String systemPrompt = promptTemplateRenderer.renderQueryRewritePrompt(
                    config.getPrompt(),
                    context.getRuntimeVariables());
            List<ChatHistoryMessage> rewriteHistory =
                    context.getRuntimeVariables().require(
                            RuntimeVariableAccessors.QUERY_REWRITE_HISTORY);
            ChatCompletionCommand command = new ChatCompletionCommand(
                    originalQuery,
                    systemPrompt,
                    rewriteHistory,
                    config.getModelConfig());
            ChatCompletionResult result = Objects.requireNonNull(
                    chatCompletionGateway.complete(command)
                            .block(Duration.ofMillis(config.getTimeoutMillis())),
                    "问题改写模型未返回结果");
            context.recordModelUsage(result.usage());
            writeRetrievalQuery(context, validateAndNormalize(result.content(), config));
            return ChatProcessResult.CONTINUE;
        } catch (RuntimeException exception) {
            return handleFailure(context, originalQuery, config, exception);
        }
    }

    private static String validateAndNormalize(String content, QueryRewriteConfig config) {
        String rewrittenQuery = Objects.requireNonNullElse(content, "").replaceAll("\\s+", " ").trim();
        if (rewrittenQuery.isBlank()) {
            throw invalidResult("改写结果为空");
        }
        if (rewrittenQuery.length() > config.getMaxOutputCharacters()) {
            throw invalidResult("改写结果超过字符上限");
        }
        if (rewrittenQuery.contains("```")) {
            throw invalidResult("改写结果包含代码围栏");
        }
        return rewrittenQuery;
    }

    private static ChatProcessResult handleFailure(ModelChatContext context, String originalQuery, QueryRewriteConfig config, RuntimeException exception) {
        if (!config.isFallbackToOriginal()) {
            BusinessException failure = new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题改写失败，请稍后重试");
            failure.initCause(exception);
            throw failure;
        }
        log.warn("问题改写失败，按AgentVersion策略回退原问题。modelInstanceId={}, promptVersionId={}", config.getModelConfig().getInstanceId(), config.getPrompt().promptVersionId(), exception);
        writeRetrievalQuery(context, originalQuery);
        return ChatProcessResult.CONTINUE;
    }

    private static BusinessException invalidResult(String reason) {
        return new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题改写结果无效：" + reason);
    }

    private static void writeRetrievalQuery(ModelChatContext context, String query) {
        context.getRuntimeVariables().put(RuntimeVariableAccessors.KNOWLEDGE_RETRIEVAL_QUERY, query);
    }
}
