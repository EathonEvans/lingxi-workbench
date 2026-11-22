package com.lingxi.workbench.admin.knowledge.service.strategy;

import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切片策略：按 Token 预算严格切分，尽量在自然边界处分割。
 *
 * <p>同时提供字符边界调整和重叠计算等底层方法，供需要降级的上级策略复用。</p>
 */
public class FixedChunker implements ChunkStrategy {

    private static final double MIN_BOUNDARY_RATIO = 0.6D;

    private final TokenCountEstimator tokenEstimator;

    public FixedChunker(TokenCountEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 固定长度切片：按 Token 预算逐段切割，每段尽量在句子/换行等自然边界结束。
     *
     * <p>核心流程：</p>
     * <ol>
     *   <li>以 Token 预算确定当前块的字符范围（扩步长 + 二分查找）</li>
     *   <li>将边界回退到最近的句子末尾或换行符，避免截断语义</li>
     *   <li>根据 overlap 预算计算下一块的起始偏移，实现相邻块之间的内容重叠</li>
     *   <li>兜底：若自然边界调整导致空块，则至少保留一个字符</li>
     * </ol>
     *
     * @see #findMaxEnd(String, int, int)
     * @see #adjustToNaturalBoundary(String, int, int)
     * @see #findOverlapStart(String, int, int, int)
     */
    @Override
    public List<DraftChunk> chunk(String content, int chunkSize, int chunkOverlap, String sectionTitle) {
        List<DraftChunk> chunks = new ArrayList<>();
        int start = 0;

        while (start < content.length()) {
            // 1. 在 Token 预算内找到最远的字符位置
            int end = findMaxEnd(content, start, chunkSize);

            // 2. 将 end 回退到自然边界（句号、换行等），避免在词语中间截断
            end = adjustToNaturalBoundary(content, start, end);

            // 3. 兜底：自然边界调整可能导致 end <= start，此时至少取一个字符
            if (end <= start) {
                end = Math.min(content.length(), start + 1);
            }

            // 4. 生成当前 Chunk
            chunks.add(new DraftChunk(content.substring(start, end).trim(), sectionTitle));

            if (end >= content.length()) {
                break;
            }

            // 5. 根据 overlap 预算计算下一块的起始位置，实现块间内容重叠
            //    nextStart <= start 表示无法有效后退，直接用 end 作为下一块起点
            int nextStart = findOverlapStart(content, start, end, chunkOverlap);
            start = nextStart <= start ? end : nextStart;
        }
        return chunks;
    }

    /**
     * 提取文本末尾不超过指定 Token 预算的后缀，用于上级策略的段落间重叠计算。
     */
    String suffixWithinBudget(String content, int tokenBudget) {
        if (content.isBlank() || tokenBudget == 0) {
            return "";
        }
        return content.substring(findOverlapStart(content, 0, content.length(), tokenBudget)).trim();
    }

    /**
     * 二分查找在 Token 预算内可切分的最远字符位置。
     */
    private int findMaxEnd(String content, int start, int tokenBudget) {
        int low = nextCharBoundary(content, start + 1);
        int high = content.length();
        int best = low;
        while (low <= high) {
            int rawMiddle = (low + high) >>> 1;
            int middle = previousCharBoundary(content, rawMiddle);
            if (middle <= start) {
                low = rawMiddle + 1;
                continue;
            }
            if (tokenEstimator.estimate(content.substring(start, middle)) <= tokenBudget) {
                best = middle;
                low = rawMiddle + 1;
            } else {
                high = rawMiddle - 1;
            }
        }
        return best;
    }

    /**
     * 二分查找满足重叠 Token 预算的下一个 Chunk 起始位置。
     */
    private int findOverlapStart(String content, int chunkStart, int chunkEnd, int overlapBudget) {
        if (overlapBudget == 0) {
            return chunkEnd;
        }
        int low = chunkStart;
        int high = chunkEnd - 1;
        int best = chunkEnd;
        while (low <= high) {
            int rawMiddle = (low + high) >>> 1;
            int middle = previousCharBoundary(content, rawMiddle);
            int tokens = tokenEstimator.estimate(content.substring(middle, chunkEnd));
            if (tokens <= overlapBudget) {
                best = middle;
                high = rawMiddle - 1;
            } else {
                low = rawMiddle + 1;
            }
        }
        return best;
    }

    /**
     * 将切分边界调整到句子末尾或换行符等自然边界处。
     */
    private static int adjustToNaturalBoundary(String content, int start, int proposedEnd) {
        if (proposedEnd >= content.length()) {
            return proposedEnd;
        }
        int minimum = start + (int) ((proposedEnd - start) * MIN_BOUNDARY_RATIO);
        for (int index = proposedEnd - 1; index >= minimum; index--) {
            char current = content.charAt(index);
            if (current == '\n' || current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return proposedEnd;
    }

    /**
     * 确保索引不落在代理对中间，向前调整到字符边界。
     */
    private static int nextCharBoundary(String content, int index) {
        if (index > 0 && index < content.length()
                && Character.isHighSurrogate(content.charAt(index - 1))
                && Character.isLowSurrogate(content.charAt(index))) {
            return index + 1;
        }
        return index;
    }

    /**
     * 确保索引不落在代理对中间，向后调整到字符边界。
     */
    private static int previousCharBoundary(String content, int index) {
        if (index > 0 && index < content.length()
                && Character.isHighSurrogate(content.charAt(index - 1))
                && Character.isLowSurrogate(content.charAt(index))) {
            return index - 1;
        }
        return index;
    }
}
