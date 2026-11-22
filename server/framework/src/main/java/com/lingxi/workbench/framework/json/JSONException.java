package com.lingxi.workbench.framework.json;

/**
 * 项目 JSON 边界统一抛出的运行时异常。
 *
 * <p>将 Gson 的解析异常收敛到项目自己的异常类型，可以让调用方不依赖 Gson
 * 的异常层次，同时保留原始原因便于定位损坏的快照、消息或请求体。</p>
 */
public class JSONException extends RuntimeException {

    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }
}
