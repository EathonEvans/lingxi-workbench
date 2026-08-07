package com.lingxi.workbench.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 为不可变Planner决策快照生成确定性SHA-256。 */
@Component
public class ExecutionDecisionChecksum {

    private final JsonMapper canonicalObjectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public String calculate(ExecutionDecision decision) {
        try {
            byte[] canonicalJson = canonicalObjectMapper.writeValueAsBytes(decision);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Planner决策快照计算失败");
        }
    }
}
