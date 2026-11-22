package com.lingxi.workbench.workflow.common.constants;

/** 意图路由发布协议及平台硬边界。 */
public interface IntentRoutingConstants {

    int INTENT_TREE_SCHEMA_VERSION = 2;

    int MIN_TOP_K = 1;

    int MAX_TOP_K = 20;

    int MIN_OUTPUT_TOKENS = 64;

    int MAX_OUTPUT_TOKENS = 4096;

    int MIN_TIMEOUT_SECONDS = 1;

    int MAX_TIMEOUT_SECONDS = 60;

    int MAX_REPEAT_ATTEMPTS = 1;
}
