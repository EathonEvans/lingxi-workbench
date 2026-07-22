package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切片器。
 *
 * <p>Chunk Size和Overlap均以Token为单位。固定长度策略严格按Token预算切分；段落和标题策略
 * 优先保持语义边界，单个语义单元超限时再退化为固定长度切分。</p>
 */
@Component
public class KnowledgeDocumentChunker {
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final double MIN_BOUNDARY_RATIO = 0.6D;

    private final TokenCountEstimator tokenEstimator = new JTokkitTokenCountEstimator();

    /**
     * 根据指定策略将标准化正文切分为顺序稳定的Chunk。
     */
    public List<KnowledgeChunkBO> chunk(String content, KnowledgeChunkStrategy strategy, int chunkSize, int chunkOverlap) {
        validatePolicy(strategy, chunkSize, chunkOverlap);
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "待切片文档正文不能为空");
        }

        List<DraftChunk> drafts = switch (strategy) {
            case FIXED -> splitFixed(normalized, chunkSize, chunkOverlap, null);
            case PARAGRAPH -> splitParagraphs(normalized, chunkSize, chunkOverlap, null);
            case HEADING -> splitByHeadings(normalized, chunkSize, chunkOverlap);
        };
        List<KnowledgeChunkBO> chunks = new ArrayList<>(drafts.size());
        for (DraftChunk draft : drafts) {
            String chunkContent = draft.content().trim();
            if (!chunkContent.isBlank()) {
                chunks.add(new KnowledgeChunkBO(chunks.size() + 1, chunkContent, tokenEstimator.estimate(chunkContent), draft.sectionTitle()));
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档未生成有效Chunk");
        }
        return List.copyOf(chunks);
    }

    /**
     * 固定长度切分：按Token预算严格切分，尽量在自然边界处分割。
     */
    private List<DraftChunk> splitFixed(String content, int chunkSize, int chunkOverlap, String sectionTitle) {
        List<DraftChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = findMaxEnd(content, start, chunkSize);
            end = adjustToNaturalBoundary(content, start, end);
            if (end <= start) {
                end = Math.min(content.length(), start + 1);
            }
            chunks.add(new DraftChunk(content.substring(start, end).trim(), sectionTitle));
            if (end >= content.length()) {
                break;
            }
            int nextStart = findOverlapStart(content, start, end, chunkOverlap);
            start = nextStart <= start ? end : nextStart;
        }
        return chunks;
    }

    /**
     * 段落切分：优先保持段落完整，超长段落退化为固定长度切分。
     */
    private List<DraftChunk> splitParagraphs(String content, int chunkSize, int chunkOverlap, String sectionTitle) {
        List<DraftChunk> chunks = new ArrayList<>();
        List<String> paragraphs = Arrays.stream(PARAGRAPH_SEPARATOR.split(content))
                .map(String::trim)
                .filter(paragraph -> !paragraph.isBlank())
                .toList();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (tokenEstimator.estimate(paragraph) > chunkSize) {
                flush(current, chunks, sectionTitle);
                chunks.addAll(splitFixed(paragraph, chunkSize, chunkOverlap, sectionTitle));
                continue;
            }
            String candidate = current.isEmpty() ? paragraph : current + "\n\n" + paragraph;
            if (tokenEstimator.estimate(candidate) <= chunkSize) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            String previous = current.toString();
            flush(current, chunks, sectionTitle);
            String overlap = suffixWithinBudget(previous, chunkOverlap);
            String withOverlap = overlap.isBlank() ? paragraph : overlap + "\n\n" + paragraph;
            current.append(tokenEstimator.estimate(withOverlap) <= chunkSize ? withOverlap : paragraph);
        }
        flush(current, chunks, sectionTitle);
        return chunks;
    }

    /**
     * 标题感知切分：按Markdown标题结构分段，每段内再按段落策略切分。
     */
    private List<DraftChunk> splitByHeadings(String content, int chunkSize, int chunkOverlap) {
        List<Section> sections = parseSections(content);
        if (sections.stream().noneMatch(section -> section.title() != null)) {
            return splitParagraphs(content, chunkSize, chunkOverlap, null);
        }
        List<DraftChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            chunks.addAll(splitParagraphs(section.content(), chunkSize, chunkOverlap, section.title()));
        }
        return chunks;
    }

    /**
     * 解析Markdown标题层级，构建层次化的标题路径。
     */
    private static List<Section> parseSections(String content) {
        List<Section> sections = new ArrayList<>();
        String[] headingPath = new String[6];
        String currentTitle = null;
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\n", -1)) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                addSection(sections, currentTitle, current);
                int level = matcher.group(1).length();
                Arrays.fill(headingPath, level, headingPath.length, null);
                headingPath[level - 1] = matcher.group(2).trim();
                currentTitle = Arrays.stream(headingPath).filter(title -> title != null && !title.isBlank()).reduce((left, right) -> left + " / " + right).orElse(null);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        addSection(sections, currentTitle, current);
        return sections;
    }

    /**
     * 在Token预算内找到最大可切分位置，使用扩步长+二分查找。
     */
    private int findMaxEnd(String content, int start, int tokenBudget) {
        int best = nextCharBoundary(content, start + 1);
        int span = Math.max(16, (int) Math.min(Integer.MAX_VALUE, (long) tokenBudget * 4));
        int probe = nextCharBoundary(content, Math.min(content.length(), start + span));
        while (tokenEstimator.estimate(content.substring(start, probe)) <= tokenBudget) {
            best = probe;
            if (probe >= content.length()) {
                return probe;
            }
            span = span > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : span * 2;
            probe = nextCharBoundary(content, (int) Math.min(content.length(), (long) start + span));
        }

        int low = best + 1;
        int high = probe - 1;
        while (low <= high) {
            int rawMiddle = (low + high) >>> 1;
            int middle = previousCharBoundary(content, rawMiddle);
            if (middle <= start) {
                low = rawMiddle + 1;
                continue;
            }
            int tokens = tokenEstimator.estimate(content.substring(start, middle));
            if (tokens <= tokenBudget) {
                best = Math.max(best, middle);
                low = rawMiddle + 1;
            } else {
                high = rawMiddle - 1;
            }
        }
        return best;
    }

    /**
     * 二分查找满足重叠Token预算的下一个Chunk起始位置。
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
     * 提取文本末尾不超过指定Token预算的后缀，用于段落间重叠。
     */
    private String suffixWithinBudget(String content, int tokenBudget) {
        if (content.isBlank() || tokenBudget == 0) {
            return "";
        }
        return content.substring(findOverlapStart(content, 0, content.length(), tokenBudget)).trim();
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
            if (current == '\n' || current == '。' || current == '！' || current == '？' || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return proposedEnd;
    }

    /**
     * 确保索引不落在代理对中间，向前调整到字符边界。
     */
    private static int nextCharBoundary(String content, int index) {
        if (index > 0 && index < content.length() && Character.isHighSurrogate(content.charAt(index - 1)) && Character.isLowSurrogate(content.charAt(index))) {
            return index + 1;
        }
        return index;
    }

    /**
     * 确保索引不落在代理对中间，向后调整到字符边界。
     */
    private static int previousCharBoundary(String content, int index) {
        if (index > 0 && index < content.length() && Character.isHighSurrogate(content.charAt(index - 1)) && Character.isLowSurrogate(content.charAt(index))) {
            return index - 1;
        }
        return index;
    }

    /**
     * 将当前缓冲区内容刷入Chunk列表并清空。
     */
    private static void flush(StringBuilder current, List<DraftChunk> chunks, String sectionTitle) {
        if (!current.isEmpty()) {
            chunks.add(new DraftChunk(current.toString().trim(), sectionTitle));
            current.setLength(0);
        }
    }

    /**
     * 将一个文档片段添加到段落列表并清空缓冲区。
     */
    private static void addSection(List<Section> sections, String title, StringBuilder content) {
        String sectionContent = content.toString().trim();
        if (!sectionContent.isBlank()) {
            sections.add(new Section(title, sectionContent));
        }
        content.setLength(0);
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

    private record DraftChunk(String content, String sectionTitle) {
    }

    private record Section(String title, String content) {
    }
}
