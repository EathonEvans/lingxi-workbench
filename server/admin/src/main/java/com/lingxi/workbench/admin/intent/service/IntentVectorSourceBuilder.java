package com.lingxi.workbench.admin.intent.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.springframework.stereotype.Component;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

/** 将启用叶子意图转换为可解释、可复现的向量语义来源。 */
@Component
public class IntentVectorSourceBuilder {

    /** 每个叶子生成一个原型来源，并为每条去重正例生成独立来源。 */
    public List<IntentVectorSource> build(IntentTreeDefinition definition) {
        List<IntentVectorSource> sources = new ArrayList<>();
        for (IntentNodeDefinition node : definition.nodes()) {
            if (!node.isEnabled() || node.getNodeType() != IntentNodeType.LEAF) {
                continue;
            }
            String prototype = prototype(node);
            sources.add(new IntentVectorSource(
                    node.getId(), node.getCode(), "PROTOTYPE", 0, prototype, sha256(prototype)));
            int ordinal = 0;
            List<String> positiveExamples = node.getPositiveExamples() == null
                    ? List.of()
                    : node.getPositiveExamples();
            for (String example : new LinkedHashSet<>(positiveExamples)) {
                if (CharSequenceUtil.isBlank(example)) {
                    continue;
                }
                String normalized = example.trim();
                sources.add(new IntentVectorSource(
                        node.getId(), node.getCode(), "EXAMPLE", ordinal++, normalized, sha256(normalized)));
            }
        }
        return List.copyOf(sources);
    }

    private static String prototype(IntentNodeDefinition node) {
        StringBuilder builder = new StringBuilder()
                .append("意图名称：").append(node.getName().trim())
                .append("\n语义描述：").append(node.getDescription().trim());
        List<IntentSlotDefinition> slots = node.getSlots() == null ? List.of() : node.getSlots();
        if (!slots.isEmpty()) {
            builder.append("\n所需信息：");
            for (IntentSlotDefinition slot : slots) {
                builder.append(slot.getName());
                if (CharSequenceUtil.isNotBlank(slot.getDescription())) {
                    builder.append('(').append(slot.getDescription().trim()).append(')');
                }
                builder.append('；');
            }
        }
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    /** 一条稳定语义文本及其索引定位信息。 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class IntentVectorSource {
        private final String intentNodeId;
        private final String intentCode;
        private final String vectorKind;
        private final int sourceOrdinal;
        private final String text;
        private final String sourceHash;

        public String intentNodeId() {
            return intentNodeId;
        }

        public String intentCode() {
            return intentCode;
        }

        public String vectorKind() {
            return vectorKind;
        }

        public int sourceOrdinal() {
            return sourceOrdinal;
        }

        public String text() {
            return text;
        }

        public String sourceHash() {
            return sourceHash;
        }
    }
}
