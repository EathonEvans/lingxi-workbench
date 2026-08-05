package com.lingxi.workbench.chat.model.bo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 意图路由返回的结构化澄清事件，通过 SSE 的 {@code clarification} 事件类型发给前端。
 *
 * <p>当用户请求触发了意图歧义消解或槽位缺失时，{@code IntentRoutingServiceImpl}
 * 构建此对象并通过 {@link IntentRoutingResult} 返回。上层 SSE 路由器将其序列化后
 * 以 {@code event: clarification} 帧发送。</p>
 *
 * <p>前端接收到该事件后：</p>
 * <ul>
 *   <li>对于歧义澄清：渲染 ${@code options} 列表为可选按钮，用户点击后回传
 *       {@code clarificationId + optionCode}</li>
 *   <li>对于槽位澄清：展示 ${@code question} 文本，用户在输入框中自由回复槽位值，
 *       可同时携带 {@code clarificationId} 自动关联上下文</li>
 * </ul>
 *
 * <p>同时保留普通文本 question 字段，用于兼容不支持结构化选项的旧客户端。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code clarificationId} — 澄清会话唯一标识，前端回传时携带以恢复上下文</li>
 *   <li>{@code question} — 向用户展示的澄清问题文本（兼容纯文本展示）</li>
 *   <li>{@code options} — 可选的结构化选项列表（歧义场景），每项含 stable code 和显示 label</li>
 *   <li>{@code round} — 当前澄清轮次，从 1 开始，上限 2</li>
 *   <li>{@code expiresAt} — 过期时间，超过后会话失效，下一条消息重新识别</li>
 *   <li>{@code sequence} — SSE 消息序号，用于客户端去重和断线续传</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatClarificationEventBO {

    /** 澄清会话唯一标识 */
    private String clarificationId;

    /** 澄清问题文本 */
    private String question;

    /** 可选选项列表（歧义场景），空列表表示纯文本澄清（槽位场景） */
    private List<ClarificationOptionBO> options;

    /** 当前轮次 */
    private int round;

    /** 过期时间 */
    private OffsetDateTime expiresAt;

    /** SSE 消息序号 */
    private long sequence;

    /**
     * 单个澄清选项。
     *
     * @param code  回传时使用的稳定选项编码（如意图叶子编码）
     * @param label 用户可见的名称（如意图叶子 displayName）
     */
    public record ClarificationOptionBO(String code, String label) {
    }
}