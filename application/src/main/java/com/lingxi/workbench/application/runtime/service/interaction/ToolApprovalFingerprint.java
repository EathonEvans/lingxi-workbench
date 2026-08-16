package com.lingxi.workbench.application.runtime.service.interaction;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/** 为 Tool 契约和单次精确调用生成确定性 SHA-256 指纹。 */
@Component
public class ToolApprovalFingerprint {

    private final JsonMapper canonicalMapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public String configHash(AgentToolDefinition definition) {
        if (!definition.getConfigurationHash().isBlank()) {
            return definition.getConfigurationHash();
        }
        return digest(canonical(definition));
    }

    public String approval(String runId, String toolCode, String configHash, String toolInput,
                           String resourceScope, OffsetDateTime expiresAt) {
        Object parsedInput;
        try {
            parsedInput = canonicalMapper.readValue(toolInput, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool调用参数不是有效JSON", exception);
        }
        String material = String.join("\n", runId, toolCode, configHash, canonical(parsedInput),
                resourceScope, expiresAt.toInstant().toString());
        return digest(material);
    }

    private String canonical(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Tool审批指纹序列化失败", exception);
        }
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}
