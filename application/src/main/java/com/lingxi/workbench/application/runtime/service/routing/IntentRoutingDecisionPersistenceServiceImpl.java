package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingDecisionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 在模型调用结束后，用短事务追加路由事实并关联可选澄清会话。 */
@Service
@RequiredArgsConstructor
public class IntentRoutingDecisionPersistenceServiceImpl {

    private final IntentRoutingDecisionRepo decisionRepo;
    private final IntentRoutingSessionRepo sessionRepo;

    @Transactional
    public Long append(IntentRoutingDecisionEntity decision, Long clarificationId) {
        decisionRepo.insert(decision);
        if (Objects.nonNull(clarificationId)) {
            IntentRoutingSessionEntity session = sessionRepo.findOwned(
                            clarificationId, decision.getConversationId(), decision.getUserId())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "意图决策关联的澄清会话不存在"));
            IntentClarificationContext context = Objects.requireNonNull(session.getContext(), "澄清会话上下文不能为空");
            if (Objects.isNull(context.getDecisionId())) {
                context.setDecisionId(decision.getId());
                context.setOriginalMessageId(decision.getUserMessageId());
                sessionRepo.update(session);
            }
        }
        return decision.getId();
    }
}
