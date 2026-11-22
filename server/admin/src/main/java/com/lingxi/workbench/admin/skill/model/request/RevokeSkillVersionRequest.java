package com.lingxi.workbench.admin.skill.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 撤销 SkillVersion 请求。
 *
 * @param reason 撤销原因
 */
public record RevokeSkillVersionRequest(
        @NotBlank(message = "请输入撤销原因")
        @Size(max = 500, message = "撤销原因不能超过500个字符")
        String reason) {
}
