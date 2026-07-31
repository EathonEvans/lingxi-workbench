package com.lingxi.workbench.application.runtime.repo;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.RuntimeToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Tool 运行端只读仓储。 */
@Repository
@RequiredArgsConstructor
public class ToolRuntimeRepo {

    private final RuntimeToolMapper mapper;

    /** 批量读取启用的 Tool 定义，返回顺序不作为业务契约。 */
    public List<RuntimeToolEntity> findActiveByCodes(List<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<RuntimeToolEntity>lambdaQuery()
                .in(RuntimeToolEntity::getCode, codes)
                .eq(RuntimeToolEntity::getStatus, "ACTIVE"));
    }
}
