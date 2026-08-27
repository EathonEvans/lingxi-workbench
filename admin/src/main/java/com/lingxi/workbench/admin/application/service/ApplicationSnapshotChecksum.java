package com.lingxi.workbench.admin.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 为 Application 发布快照生成确定性 SHA-256。 */
@Component
public class ApplicationSnapshotChecksum {
    private final JsonMapper canonicalObjectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    /** 将行为定义和助手身份序列化为规范内容后计算 SHA-256 摘要。 */
    public String calculate(ApplicationDefinition definition, String name, String avatar) {
        try {
            byte[] canonicalJson = canonicalObjectMapper.writeValueAsBytes(definition);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonicalJson);
            digest.update((byte) '\n');
            digest.update(("name=" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(("avatar=" + (avatar == null ? "" : avatar))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(
                    HttpStatusEnum.INTERNAL_SERVER_ERROR, "Application配置快照计算失败");
        }
    }
}
