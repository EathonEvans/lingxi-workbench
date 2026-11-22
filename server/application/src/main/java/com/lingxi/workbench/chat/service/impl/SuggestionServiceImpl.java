package com.lingxi.workbench.chat.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.config.SuggestionProperties;
import com.lingxi.workbench.chat.model.bo.SuggestionItemBO;
import com.lingxi.workbench.chat.service.SuggestionService;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 回答后主动建议评估实现：以轻量模型基于回答内容生成可点击发送的后续问题。
 *
 * <p>评估 Prompt 为平台内置系统级模板（按稳定编码读取最新已发布版本），待分析回答以用户消息传入。
 * 建议属于非必要增强，任何失败路径（开关关闭、Prompt 缺失、模型异常、输出非法）都收敛为
 * “本次无建议”，通过告警日志与失败计数保持可观测，绝不向调用方抛出异常。</p>
 */
@Slf4j
@Component
public class SuggestionServiceImpl implements SuggestionService {

    /** 内置建议评估 Prompt 的稳定编码，由迁移脚本种子发布。 */
    private static final String SUGGESTION_PROMPT_CODE = "chat_answer_suggestion";
    /** 建议标题的最大字符数，超出的非法输出直接丢弃。 */
    private static final int MAX_TITLE_CHARACTERS = 50;
    /** 建议 prompt 的最大字符数，超出的非法输出直接丢弃。 */
    private static final int MAX_PROMPT_CHARACTERS = 200;

    private final PromptRuntimeService promptRuntimeService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ChatCompletionGateway completionGateway;
    private final SuggestionProperties properties;
    private final Counter evaluateSuccessCounter;
    private final Counter evaluateFailureCounter;

    public SuggestionServiceImpl(PromptRuntimeService promptRuntimeService, ModelRuntimeConfigService modelRuntimeConfigService,
                                 ChatCompletionGateway completionGateway, SuggestionProperties properties, MeterRegistry meterRegistry) {
        this.promptRuntimeService = promptRuntimeService;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
        this.completionGateway = completionGateway;
        this.properties = properties;
        this.evaluateSuccessCounter = meterRegistry.counter("lingxi.chat.suggestion.eval.count", "outcome", "success");
        this.evaluateFailureCounter = meterRegistry.counter("lingxi.chat.suggestion.eval.count", "outcome", "failure");
    }

    @Override
    public Optional<List<SuggestionItemBO>> evaluate(String answer) {
        // 开关关闭、模型实例未配置或回答为空时不发起任何模型调用，建议能力完全静默。
        if (!properties.isEnabled() || Objects.isNull(properties.getModelInstanceId()) || CharSequenceUtil.isBlank(answer)) {
            return Optional.empty();
        }
        try {
            String systemPrompt = resolveSystemPrompt();
            ChatModelRuntimeConfig modelConfig = modelRuntimeConfigService.getChatModelConfig(properties.getModelInstanceId());
            modelConfig.setMaxTokens(properties.getMaxOutputTokens());

            // 待分析回答以用户消息传入；Prompt 模板不含变量，无需运行时变量渲染。
            ChatCompletionCommand command = new ChatCompletionCommand(
                    truncateAnswer(answer), systemPrompt, List.of(), modelConfig);
            ChatCompletionResult<String> result = Objects.requireNonNull(
                    completionGateway.complete(command).block(Duration.ofSeconds(properties.getTimeoutSeconds())),
                    "回答建议模型未返回结果");
            List<SuggestionItemBO> suggestions = parseSuggestions(result.content());
            evaluateSuccessCounter.increment();
            return Optional.of(suggestions);
        } catch (RuntimeException exception) {
            // 建议评估失败只降级为本次无建议，完成主流程不受影响；记录失败计数与告警日志保持可观测。
            evaluateFailureCounter.increment();
            log.warn("回答建议评估失败，本次回答不提供建议。modelInstanceId={}, errorType={}",
                    properties.getModelInstanceId(), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 读取内置建议评估 Prompt 的最新已发布正文。
     *
     * <p>Prompt 缺失属于部署缺陷（种子迁移未执行），记录告警后按功能关闭处理，
     * 不阻塞回答完成流程。</p>
     */
    private String resolveSystemPrompt() {
        PromptRuntimeSnapshot prompt = promptRuntimeService.resolveLatestPublishedByCode(SUGGESTION_PROMPT_CODE)
                .orElseThrow(() -> new IllegalStateException("内置建议Prompt不可用，请确认种子迁移已执行"));
        return prompt.content().trim();
    }

    private String truncateAnswer(String answer) {
        if (answer.length() <= properties.getMaxAnswerCharacters()) {
            return answer;
        }
        return answer.substring(0, properties.getMaxAnswerCharacters());
    }

    /**
     * 解析模型输出的建议 JSON 数组。
     *
     * <p>模型输出视为不可信输入：容忍 Markdown 代码块包裹，逐条校验 title 与 prompt 的
     * 非空和长度上限，非法条目直接丢弃；条数超出配置上限时截断。建议标识由服务端按序生成，
     * 不信任模型输出的 id。</p>
     */
    private List<SuggestionItemBO> parseSuggestions(String content) {
        String raw = CharSequenceUtil.nullToEmpty(content).trim();
        if (raw.isBlank()) {
            return List.of();
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("回答建议输出不是JSON数组");
        }
        JsonArray array = JsonParser.parseString(raw.substring(start, end + 1)).getAsJsonArray();
        List<SuggestionItemBO> suggestions = new ArrayList<>();
        for (JsonElement element : array) {
            if (suggestions.size() >= properties.getMaxItems()) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            String title = trimField(element, "title", MAX_TITLE_CHARACTERS);
            String prompt = trimField(element, "prompt", MAX_PROMPT_CHARACTERS);
            if (CharSequenceUtil.isBlank(title) || CharSequenceUtil.isBlank(prompt)) {
                continue;
            }
            suggestions.add(new SuggestionItemBO("suggestion-" + (suggestions.size() + 1), title, prompt));
        }
        return List.copyOf(suggestions);
    }

    /** 读取 JSON 对象中的字符串字段并做长度上限裁剪，字段缺失或非字符串返回 null。 */
    private static String trimField(JsonElement element, String fieldName, int maxCharacters) {
        JsonElement value = element.getAsJsonObject().get(fieldName);
        if (Objects.isNull(value) || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.length() > maxCharacters ? null : text;
    }
}
