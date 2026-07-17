package com.lingxi.workbench.chat.model.request;

import com.lingxi.workbench.chat.common.enums.ConversationStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 应用端会话分页查询参数。
 */
@Getter
@Setter
public class ConversationPageQuery extends PageQuery {

    /** 会话标题关键字，未传时查询全部会话。 */
    @Size(max = 100, message = "会话标题关键字不能超过100个字符")
    private String keyword;

    /** 会话状态，未传时同时查询进行中和已归档会话。 */
    private ConversationStatus status;
}
