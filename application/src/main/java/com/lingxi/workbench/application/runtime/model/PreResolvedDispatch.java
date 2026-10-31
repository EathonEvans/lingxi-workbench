package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;

import java.util.List;
import java.util.Objects;

/**
 * 预解析分发目标：由触发器等确定性入口直接指定能力，不再经过意图路由。
 *
 * <p>能力编码在执行时从冻结的 ApplicationVersion 能力目录解析，类型必须与入口声明一致。
 * 工具白名单与 AgentVersion 发布快照的工具集求交后生效，为空表示不额外收紧。</p>
 *
 * @param capabilityCode   冻统能力目录中的能力编码
 * @param capabilityType   能力类型：AGENT / WORKFLOW
 * @param allowedToolCodes 触发器预授权的工具码白名单；为空表示使用目标能力默认工具集
 */
public record PreResolvedDispatch(String capabilityCode, ApplicationCapabilityType capabilityType,
                                  List<String> allowedToolCodes) {

    public PreResolvedDispatch {
        allowedToolCodes = Objects.requireNonNullElseGet(allowedToolCodes, List::of);
    }
}
