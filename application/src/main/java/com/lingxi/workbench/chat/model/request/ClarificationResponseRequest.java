package com.lingxi.workbench.chat.model.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 对服务端 clarification 事件的可选结构化响应。 */
@Data
public class ClarificationResponseRequest {
    @Pattern(regexp = "^[1-9]\\d*$", message = "澄清会话ID格式不正确")
    private String clarificationId;
    @Size(max = 64, message = "澄清选项编码不能超过64个字符")
    private String optionCode;
    @Size(max = 2_000, message = "澄清值不能超过2000个字符")
    private String value;
}
