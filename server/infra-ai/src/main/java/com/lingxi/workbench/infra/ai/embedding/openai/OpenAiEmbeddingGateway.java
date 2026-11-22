package com.lingxi.workbench.infra.ai.embedding.openai;

import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.outbound.ModelOutboundAddressPolicy;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 基于Spring AI的OpenAI兼容Embedding实现。 */
@Component
public class OpenAiEmbeddingGateway implements EmbeddingGateway {

    private final ModelOutboundAddressPolicy outboundAddressPolicy;

    public OpenAiEmbeddingGateway(ModelOutboundAddressPolicy outboundAddressPolicy) {
        this.outboundAddressPolicy = outboundAddressPolicy;
    }

    /**
     * 对文本列表进行向量化，按输入顺序返回向量数组。
     *
     * @param texts 待向量化的文本列表
     * @param config Embedding 模型运行配置，包含地址、密钥和模型编码
     * @return 按输入顺序排列的向量数组，数量与输入一致
     */
    @Override
    public List<float[]> embed(List<String> texts, EmbeddingModelRuntimeConfig config) {
        // 出站地址安全校验：配置即使绕过管理端保存校验被篡改，运行端连接前也会被拦截。
        outboundAddressPolicy.validateOutboundAddress(config.getBaseUrl());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .model(config.getModelCode())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .maxRetries(config.getMaxRetries())
                .build();
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
        return model.embed(texts);
    }
}
