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

    private static String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

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

    /** 文档解析结果。 */
    public record ParsedDocument(String content, Integer pageCount) {}
}
