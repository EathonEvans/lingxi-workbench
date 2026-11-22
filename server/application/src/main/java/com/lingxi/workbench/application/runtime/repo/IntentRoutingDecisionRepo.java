package com.lingxi.workbench.application.runtime.repo;

import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.IntentRoutingDecisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 意图路由追加式决策事实持久化语义。 */
@Repository
@RequiredArgsConstructor
public class IntentRoutingDecisionRepo {

    private final IntentRoutingDecisionMapper mapper;

    public void insert(IntentRoutingDecisionEntity decision) {
        if (mapper.insert(decision) != 1) {
            throw new IllegalStateException("意图路由决策事实写入失败");
        }
    }
}
