package com.lingxi.workbench.admin.knowledge.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.model.bo.DocChunkBO;
import com.lingxi.workbench.admin.knowledge.service.strategy.ChunkStrategy;
import com.lingxi.workbench.admin.knowledge.service.strategy.DraftChunk;
import com.lingxi.workbench.admin.knowledge.service.strategy.FixedChunker;
import com.lingxi.workbench.admin.knowledge.service.strategy.HeadingChunker;
import com.lingxi.workbench.admin.knowledge.service.strategy.ParagraphChunker;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档切片器。
 *
 * <p>Chunk Size和Overlap均以Token为单位。通过策略组合实现降级链：
 * HEADING → PARAGRAPH → FIXED，每个策略在自身语义单元超限时自动委托给下级策略。</p>
 */
@Component
public class KnowledgeDocumentChunker {

    private final TokenCountEstimator tokenEstimator;
    private final Map<KnowledgeChunkStrategy, ChunkStrategy> strategies;

    public KnowledgeDocumentChunker() {
        this.tokenEstimator = new JTokkitTokenCountEstimator();
        FixedChunker fixedChunker = new FixedChunker(tokenEstimator);
        ParagraphChunker paragraphChunker = new ParagraphChunker(fixedChunker, tokenEstimator);
        HeadingChunker headingChunker = new HeadingChunker(paragraphChunker);
        this.strategies = Map.of(
                KnowledgeChunkStrategy.FIXED, fixedChunker,
                KnowledgeChunkStrategy.PARAGRAPH, paragraphChunker,
                KnowledgeChunkStrategy.HEADING, headingChunker
        );
    }

    /**
     * 计算人工修订后Chunk的Token数量。
     */
    public int estimateTokens(String content) {
        return tokenEstimator.estimate(content);
    }

    /**
     * 根据指定策略将标准化正文切分为顺序稳定的Chunk。
     *
     * @param content      文档解析原文
     * @param strategy     分块策略
     * @param chunkSize    块大小（Token）
     * @param chunkOverlap 重叠大小（Token）
     * @return {@link List<DocChunkBO>} 文档正文分块结果
     */
    public List<DocChunkBO> chunk(String content, KnowledgeChunkStrategy strategy, int chunkSize, int chunkOverlap) {
        // chunk参数严格校验
        validatePolicy(strategy, chunkSize, chunkOverlap);
        // 标准化正文
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待切片文档正文不能为空");
        }
        // 确认分块策略并执行分块
        List<DraftChunk> drafts = strategies.get(strategy)
                .chunk(normalized, chunkSize, chunkOverlap, null);
        // 组装BO，
        List<DocChunkBO> chunks = new ArrayList<>(drafts.size());
        for (DraftChunk draft : drafts) {
            String chunkContent = draft.content().trim();
            if (CharSequenceUtil.isBlankOrUndefined(chunkContent)) {
                continue;
            }
            int chunkNo = chunks.size() + 1;
            int chunkToken = tokenEstimator.estimate(chunkContent);
            DocChunkBO chunkBO = new DocChunkBO(chunkNo, chunkContent, chunkToken, draft.sectionTitle());
            chunks.add(chunkBO);
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档未生成有效Chunk");
        }
        return List.copyOf(chunks);
    }

    /**
     * 校验切片策略参数合法性。
     */
    private static void validatePolicy(KnowledgeChunkStrategy strategy, int chunkSize, int chunkOverlap) {
        if (strategy == null) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "切片策略不能为空");
        }
        if (chunkSize <= 0) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Chunk Size必须大于0");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Chunk Overlap必须大于等于0且小于Chunk Size");
        }
    }

    /**
     * 标准化文本换行符并去除首尾空白。
     */
    private static String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
