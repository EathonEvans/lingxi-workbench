package com.lingxi.workbench.framework.cache;

/**
 * Redis Key 常量定义，统一管理缓存 Key 前缀，确保不同服务下的 Key 命名空间隔离。
 */
public interface RedisKeyConstants {

    /** 全局缓存 Key 前缀，避免与外部服务 Key 冲突。 */
    String CACHE_PREFIX = "lx_wbc:";

}
