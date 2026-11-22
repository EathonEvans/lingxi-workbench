package com.lingxi.workbench.application.runtime.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeMcpServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.lingxi.workbench.application.runtime.common.constants.McpRuntimeConstants.ACTIVE_STATUS;
import static com.lingxi.workbench.application.runtime.common.constants.McpRuntimeConstants.STREAMABLE_HTTP_TRANSPORT;
import static com.lingxi.workbench.application.runtime.common.constants.McpRuntimeConstants.SUCCESS_TEST_STATUS;

/**
 * MCP Server 运行端只读仓储。
 */
@Repository
@RequiredArgsConstructor
public class McpServerRuntimeRepo {

    private final RuntimeMcpServerMapper mapper;

    /**
     * 批量读取可运行的 MCP Server，避免按 Tool 逐条查询。
     */
    public List<RuntimeMcpServerEntity> findRuntimeAvailableByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<RuntimeMcpServerEntity>lambdaQuery()
                .in(RuntimeMcpServerEntity::getId, ids)
                .eq(RuntimeMcpServerEntity::getStatus, ACTIVE_STATUS)
                .eq(RuntimeMcpServerEntity::getLastTestStatus, SUCCESS_TEST_STATUS)
                .eq(RuntimeMcpServerEntity::getTransportType, STREAMABLE_HTTP_TRANSPORT));
    }
}
