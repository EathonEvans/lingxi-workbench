package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;

/** 按ApplicationVersion锁定快照执行单项能力。 */
public interface CapabilityExecutionService {

    CapabilityExecutionResult execute(CapabilityExecutionRequest request);
}
