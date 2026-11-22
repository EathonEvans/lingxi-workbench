package com.lingxi.workbench.admin.knowledge.service.strategy;

import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 段落优先切片策略：优先保持段落完整，超长段落退化为固定长度切分。
 */
public class ParagraphChunker implements ChunkStrategy {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n\\s*\\n+");

    private final FixedChunker fixedChunker;
    private final TokenCountEstimator tokenEstimator;

    public ParagraphChunker(FixedChunker fixedChunker, TokenCountEstimator tokenEstimator) {
        this.fixedChunker = fixedChunker;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 段落优先切片：以空行为边界将文本拆为段落，按 Token 预算贪婪打包。
     *
     * <p>核心策略：</p>
     * <ol>
     *   <li>整体通过空行分割成段落列表</li>
     *   <li>依次尝试将段落拼入当前缓冲区，撑满一个 Chunk 后刷出</li>
     *   <li>单个段落超过 chunkSize 时，委托 FixedChunker 做固定长度切分</li>
     *   <li>Chunk 之间通过上一块的尾缀实现重叠，保持语义连贯</li>
     * </ol>
     */
    @Override
    public List<DraftChunk> chunk(String content, int chunkSize, int chunkOverlap, String sectionTitle) {
        List<DraftChunk> chunks = new ArrayList<>();

        // 1. 按空行切割，过滤空白段落
        List<String> paragraphs = Arrays.stream(PARAGRAPH_SEPARATOR.split(content))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();

        // 2. 贪婪打包：逐段尝试拼入缓冲区，满则刷出
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (tokenEstimator.estimate(paragraph) > chunkSize) {
                // 分支 A：单段超限 → 刷出缓冲，委托 FixedChunker 切分
                flush(current, chunks, sectionTitle);
                chunks.addAll(fixedChunker.chunk(paragraph, chunkSize, chunkOverlap, sectionTitle));
                continue;
            }

            String candidate = current.isEmpty() ? paragraph : current + "\n\n" + paragraph;
            if (tokenEstimator.estimate(candidate) <= chunkSize) {
                // 分支 B：容量充足 → 合入当前 Chunk
                current.setLength(0);
                current.append(candidate);
            } else {
                // 分支 C：已满 → 刷出旧块，取旧块尾缀作为 overlap 前缀起新块
                startNextChunk(current, paragraph, chunkSize, chunkOverlap, sectionTitle, chunks);
            }
        }

        // 3. 收尾
        flush(current, chunks, sectionTitle);
        return chunks;
    }

    /**
     * 刷出当前 Chunk 后启动新块：取上一块末尾不超过 overlap 预算的尾缀作为上下文前缀，
     * 拼入当前段落。若拼接后超出 chunkSize，则舍去前缀。
     */
    private void startNextChunk(StringBuilder current, String paragraph,
                                 int chunkSize, int chunkOverlap, String sectionTitle,
                                 List<DraftChunk> chunks) {
        String previous = current.toString();
        flush(current, chunks, sectionTitle);
        String suffix = fixedChunker.suffixWithinBudget(previous, chunkOverlap);
        if (suffix.isBlank()) {
            current.append(paragraph);
            return;
        }
        String withOverlap = suffix + "\n\n" + paragraph;
        current.append(tokenEstimator.estimate(withOverlap) <= chunkSize ? withOverlap : paragraph);
    }

    /**
     * 将当前缓冲区内容刷入 Chunk 列表并清空。
     */
    private void flush(StringBuilder current, List<DraftChunk> chunks, String sectionTitle) {
        if (current.isEmpty()) {
            return;
        }
        chunks.add(new DraftChunk(current.toString().trim(), sectionTitle));
        current.setLength(0);
    }
}
