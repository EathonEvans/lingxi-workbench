package com.lingxi.workbench.infra.ai.token;

/** 对供应商无感的文本 Token 估算边界。 */
@FunctionalInterface
public interface TextTokenEstimator {

    int estimate(String text);
}
