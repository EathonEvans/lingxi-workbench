package com.lingxi.workbench.application.runtime.service.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import io.agentscope.core.state.Task;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 为不可变 AgentScope 任务快照生成确定性 SHA-256。 */
@Component
public class ExecutionTaskChecksum {

    private final JsonMapper canonicalObjectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public String calculate(List<Task> tasks) {
        try {
            byte[] canonicalJson = canonicalObjectMapper.writeValueAsBytes(tasks);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "AgentScope任务快照计算失败");
        }
    }
}
