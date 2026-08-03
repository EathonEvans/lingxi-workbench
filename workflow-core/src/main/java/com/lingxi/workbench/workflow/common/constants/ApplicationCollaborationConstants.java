package com.lingxi.workbench.workflow.common.constants;

/** Application 多 Agent 协作协议的稳定边界。 */
public interface ApplicationCollaborationConstants {

    int APPLICATION_SCHEMA_VERSION = 2;

    int MIN_MEMBER_AGENTS = 1;

    int MAX_MEMBER_AGENTS = 8;

    int MIN_DELEGATIONS = 1;

    int MAX_DELEGATIONS = 10;

    int SUPERVISOR_MAX_DEPTH = 1;
}
