package com.lingxi.workbench.admin.mcp.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.mcp.model.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP Server MyBatis 数据访问层。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {
}
