package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 知识库文档解析器测试。 */
class KnowledgeDocumentParserTest {

    @Test
    void shouldParseUtf8TextAndNormalizeLineBreaks() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties(new KnowledgeProperties.Parsing(1024, 10000));
        KnowledgeDocumentParser parser = new KnowledgeDocumentParser(properties);
        ByteArrayInputStream input = new ByteArrayInputStream(
                "标题\r\n\r\n正文内容".getBytes(StandardCharsets.UTF_8));

        KnowledgeDocumentParser.ParsedDocument result = parser.parse(input, "示例.txt");

        assertThat(result.content()).isEqualTo("标题\n\n正文内容");
    }
}
