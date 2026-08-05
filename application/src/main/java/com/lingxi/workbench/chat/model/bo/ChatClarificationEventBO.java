package com.lingxi.workbench.chat.model.bo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/** 服务端发给用户的结构化澄清问题。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatClarificationEventBO {
    private String clarificationId;
    private String question;
    private List<ClarificationOptionBO> options;
    private int round;
    private OffsetDateTime expiresAt;
    private long sequence;

    /** @param code 回传时使用的稳定选项编码 @param label 用户可见名称 */
    public record ClarificationOptionBO(String code, String label) {
    }
}
