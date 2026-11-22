package com.lingxi.workbench.admin.knowledge.service.strategy;

import java.util.List;

/**
 * 文档切片策略接口。
 *
 * <p>各实现独立负责一种切片方式，需要降级时通过构造器注入更基础的策略实现组合。</p>
 */
@FunctionalInterface
public interface ChunkStrategy {

    /**
     * 将文本内容切分为 DraftChunk 列表。
     *
     * @param content      待切片文本
     * @param chunkSize    切片大小（Token 数）
     * @param chunkOverlap 切片重叠（Token 数）
     * @param sectionTitle 当前所属的章节标题，无章节时为 null
     * @return 切片列表
     */
    List<DraftChunk> chunk(String content, int chunkSize, int chunkOverlap, String sectionTitle);
}
