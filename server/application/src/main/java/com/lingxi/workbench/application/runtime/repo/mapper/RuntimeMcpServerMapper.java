package com.lingxi.workbench.application.runtime.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP Server 运行端只读 Mapper。
 */
@Mapper
public interface RuntimeMcpServerMapper extends BaseMapper<RuntimeMcpServerEntity> {
}
