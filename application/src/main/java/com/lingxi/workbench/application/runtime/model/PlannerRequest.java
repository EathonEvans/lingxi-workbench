package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/** 初始规划或重规划所需的共享上下文与受控观察。 */
@Getter
@Builder
public class PlannerRequest {

    private ConversationContext conversationContext;
    private Integer planNo;
    private String failureObservation;
    private Set<String> confirmedCapabilityCodes;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
