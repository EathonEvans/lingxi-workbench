package com.lingxi.workbench.notification.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 批量标记通知已读请求。 */
@Getter
@Setter
public class MarkNotificationsReadRequest {

    /** 待标记已读的通知主键列表。 */
    @NotEmpty(message = "通知ID列表不能为空")
    private List<Long> ids;
}
