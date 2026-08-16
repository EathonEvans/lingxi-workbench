package com.lingxi.workbench.application.runtime.controller;

import com.lingxi.workbench.application.runtime.model.request.InteractionDecisionRequest;
import com.lingxi.workbench.application.runtime.model.vo.InteractionDecisionVO;
import com.lingxi.workbench.application.runtime.model.vo.PendingInteractionVO;
import com.lingxi.workbench.application.runtime.service.interaction.HitlDecisionService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.context.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HITL 审批与待处理交互恢复接口。 */
@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
public class HitlInteractionController {

    private final HitlDecisionService service;

    @PostMapping("/{id}/decision")
    public ApiResponse<InteractionDecisionVO> decide(
            @PathVariable @Positive Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InteractionDecisionRequest request) {
        return ApiResponse.success(service.decide(id, UserContext.userId(), idempotencyKey, request));
    }

    @GetMapping("/pending")
    public ApiResponse<List<PendingInteractionVO>> pending(@RequestParam @Positive Long conversationId) {
        return ApiResponse.success(service.pending(conversationId, UserContext.userId()));
    }
}
