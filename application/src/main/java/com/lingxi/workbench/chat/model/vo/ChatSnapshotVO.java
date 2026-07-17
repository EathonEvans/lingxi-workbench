package com.lingxi.workbench.chat.model.vo;

/** 重新订阅时从数据库恢复的回答快照。 */
public record ChatSnapshotVO(
        /** 已生成的回答内容。 */
        String content) {}
