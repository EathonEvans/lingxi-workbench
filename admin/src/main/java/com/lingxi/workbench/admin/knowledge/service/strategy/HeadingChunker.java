package com.lingxi.workbench.admin.knowledge.service.strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题感知切片策略：按 Markdown 标题结构分段，每段内再按段落策略切分。
 */
public class HeadingChunker implements ChunkStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    private final ChunkStrategy paragraphChunker;

    public HeadingChunker(ChunkStrategy paragraphChunker) {
        this.paragraphChunker = paragraphChunker;
    }

    /**
     * 标题感知切片：先解析 Markdown 标题结构，每个标题段内委派 ParagraphChunker 处理。
     *
     * <p>策略层级：</p>
     * <ol>
     *   <li>调用 parseSections 解析出一组 {@link Section}，每个携带层级化的标题路径</li>
     *   <li>若全文无标题，直接降级为段落策略处理</li>
     *   <li>每个标题段的内容委派 ParagraphChunker 切分，并附带标题路径作为 sectionTitle</li>
     * </ol>
     *
     * <p>标题路径示例：输入 "# 概览\n...\n## 安装\n..." 将产出标题 "概览" 和 "概览 / 安装"。</p>
     *
     * @see #parseSections(String)
     */
    @Override
    public List<DraftChunk> chunk(String content, int chunkSize, int chunkOverlap, String sectionTitle) {
        // 1. 解析 Markdown 标题结构，将全文拆为多个标题段
        List<Section> sections = parseSections(content);

        // 2. 无标题时降级为段落策略
        if (sections.stream().noneMatch(section -> section.title() != null)) {
            return paragraphChunker.chunk(content, chunkSize, chunkOverlap, null);
        }

        // 3. 每个标题段的内容委派 ParagraphChunker 切分，附带层级化标题路径
        List<DraftChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            chunks.addAll(paragraphChunker.chunk(section.content(), chunkSize, chunkOverlap, section.title()));
        }
        return chunks;
    }

    /**
     * 解析 Markdown 标题层级，构建层次化的标题路径。
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
                currentTitle = Arrays.stream(headingPath)
                        .filter(title -> title != null && !title.isBlank())
                        .reduce((left, right) -> left + " / " + right)
                        .orElse(null);
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
     * 将一个文档片段添加到段落列表并清空缓冲区。
     */
    private static void addSection(List<Section> sections, String title, StringBuilder content) {
        String sectionContent = content.toString().trim();
        if (!sectionContent.isBlank()) {
            sections.add(new Section(title, sectionContent));
        }
        content.setLength(0);
    }
}
