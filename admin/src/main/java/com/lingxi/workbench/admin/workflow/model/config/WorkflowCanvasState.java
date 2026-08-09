package com.lingxi.workbench.admin.workflow.model.config;

import java.util.Map;

/**
 * Workflow 编辑器画布状态，不参与发布定义校验和运行时执行。
 *
 * @param nodePositions 节点 ID 到坐标的映射
 * @param viewport      画布视口
 */
public record WorkflowCanvasState(Map<String, NodePosition> nodePositions, Viewport viewport) {

    public WorkflowCanvasState {
        nodePositions = nodePositions == null ? Map.of() : Map.copyOf(nodePositions);
        viewport = viewport == null ? new Viewport(0, 0, 1) : viewport;
    }

    /**
     * 节点二维坐标。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    public record NodePosition(double x, double y) {
    }

    /**
     * 画布视口。
     *
     * @param x    横向偏移
     * @param y    纵向偏移
     * @param zoom 缩放比例
     */
    public record Viewport(double x, double y, double zoom) {
    }
}
