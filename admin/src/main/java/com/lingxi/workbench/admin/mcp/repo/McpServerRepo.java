package com.lingxi.workbench.admin.mcp.repo;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.mcp.model.entity.McpServerEntity;
import com.lingxi.workbench.admin.mcp.model.request.McpServerPageQuery;
import com.lingxi.workbench.admin.mcp.repo.mapper.McpServerMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * MCP Server 数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class McpServerRepo {

    private final McpServerMapper mapper;

    public PageResult<McpServerEntity> findPage(McpServerPageQuery query) {
        IPage<McpServerEntity> page = mapper.selectPage(query.toPage(), Wrappers.<McpServerEntity>lambdaQuery()
                .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper.like(McpServerEntity::getName, query.getKeyword()).or().like(McpServerEntity::getCode, query.getKeyword()))
                .eq(Objects.nonNull(query.getStatus()), McpServerEntity::getStatus, query.getStatus())
                .eq(Objects.nonNull(query.getLastTestStatus()), McpServerEntity::getLastTestStatus, query.getLastTestStatus())
                .orderByDesc(McpServerEntity::getUpdatedAt)
                .orderByDesc(McpServerEntity::getId));
        return PageResult.convert(page);
    }

    public Optional<McpServerEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public boolean existsByCode(String code) {
        return mapper.exists(Wrappers.<McpServerEntity>lambdaQuery().eq(McpServerEntity::getCode, code));
    }

    public void save(McpServerEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
            return;
        }
        mapper.updateById(entity);
    }
}
