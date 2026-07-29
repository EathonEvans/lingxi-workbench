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

    /** 将 ApplicationDefinition 序列化为字段排序的规范 JSON 后计算 SHA-256 摘要。 */
    public String calculate(ApplicationDefinition definition) {
        try {
            byte[] canonicalJson = canonicalObjectMapper.writeValueAsBytes(definition);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(
                    HttpStatusEnum.INTERNAL_SERVER_ERROR, "Application配置快照计算失败");
        }
    }
}
