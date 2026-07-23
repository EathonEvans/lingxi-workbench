package com.lingxi.workbench.infra.ai.embedding.openai;

import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 基于Spring AI的OpenAI兼容Embedding实现。 */
@Component
public class OpenAiCompatibleEmbeddingGateway implements EmbeddingGateway {

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingModelRuntimeConfig config) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .model(config.modelCode())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .maxRetries(config.maxRetries())
                .build();
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
        return model.embed(texts);
    }
}
