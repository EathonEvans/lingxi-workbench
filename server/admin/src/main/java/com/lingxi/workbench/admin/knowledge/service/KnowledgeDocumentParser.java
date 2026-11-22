package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/** 使用Apache Tika统一解析TXT、Markdown、PDF和DOCX正文。 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentParser {
    private static final List<String> PAGE_COUNT_KEYS = List.of("xmpTPg:NPages", "Page-Count", "meta:page-count");
    private final KnowledgeProperties properties;

    /** 使用Tika解析文档输入流，返回标准化正文和页数。 */
    public ParsedDocument parse(InputStream inputStream, String fileName) throws Exception {
        Tika tika = new Tika();
        Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName);
        String content = normalize(tika.parseToString(inputStream, metadata, properties.parsing().maxCharacters()));
        if (content.isBlank()) {
            throw new IllegalArgumentException("文档未解析出有效文本");
        }
        return new ParsedDocument(content, resolvePageCount(metadata));
    }

    /** 标准化文本换行符并去除首尾空白。 */
    private static String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /** 尝试从Tika元数据中提取可靠的页数信息，失败返回null。 */
    private static Integer resolvePageCount(Metadata metadata) {
        for (String key : PAGE_COUNT_KEYS) {
            String value = metadata.get(key);
            if (value != null) {
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException ignored) {
                    // 不可靠的页数元数据不应阻断正文解析。
                }
            }
        }
        return null;
    }

    /**
     * 文档解析结果。
     *
     * @param content   标准化正文
     * @param pageCount 页数，无可靠元数据时为null
     */
    public record ParsedDocument(String content, Integer pageCount) {}
}
