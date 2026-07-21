package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 文档切片策略测试。 */
class KnowledgeDocumentChunkerTest {
    private final KnowledgeDocumentChunker chunker = new KnowledgeDocumentChunker();

    @Test
    void shouldSplitFixedChunksWithinTokenBudget() {
        String content = "灵析工作台用于构建企业智能应用。".repeat(80);

        List<KnowledgeChunkBO> chunks = chunker.chunk(
                content, KnowledgeChunkStrategy.FIXED, 40, 8);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(40);
            assertThat(chunk.contentHash()).hasSize(64);
        });
        assertThat(chunks).extracting(KnowledgeChunkBO::chunkNo)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, chunks.size()).boxed().toList());
    }

    @Test
    void shouldKeepParagraphBoundaryWheneverPossible() {
        String first = "第一段介绍产品定位和主要使用场景。";
        String second = "第二段说明部署方式和权限要求。";

        List<KnowledgeChunkBO> chunks = chunker.chunk(
                first + "\n\n" + second, KnowledgeChunkStrategy.PARAGRAPH, 20, 2);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo(first);
        assertThat(chunks.get(1).content()).contains(second);
    }

    @Test
    void shouldExposeHeadingPathForHeadingAwareChunks() {
        String content = "# 产品手册\n\n总览内容。\n\n## 安装\n\n安装步骤说明。";

        List<KnowledgeChunkBO> chunks = chunker.chunk(
                content, KnowledgeChunkStrategy.HEADING, 100, 10);

        assertThat(chunks).extracting(KnowledgeChunkBO::sectionTitle)
                .containsExactly("产品手册", "产品手册 / 安装");
        assertThat(chunks.get(1).content()).contains("## 安装", "安装步骤说明");
    }

    @Test
    void shouldRejectInvalidOverlap() {
        assertThatThrownBy(() -> chunker.chunk(
                "有效正文", KnowledgeChunkStrategy.FIXED, 20, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldNotBreakUnicodeCodePointBoundary() {
        List<KnowledgeChunkBO> chunks = chunker.chunk(
                "智能应用🚀知识库🌟切片".repeat(10), KnowledgeChunkStrategy.FIXED, 8, 2);

        assertThat(chunks).allSatisfy(chunk -> {
            String content = chunk.content();
            assertThat(content).doesNotMatch(".*[\\uD800-\\uDBFF]$");
            assertThat(content).doesNotMatch("^[\\uDC00-\\uDFFF].*");
        });
    }
}
