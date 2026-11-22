package com.lingxi.workbench.admin.appmonitor.model.vo;

import java.util.List;

/**
 * @param sourceAvailable Application运维数据源是否可访问
 * @param message         数据源状态说明
 * @param records         不含敏感正文的死信定位信息
 */
public record ConversationDlqResultVO(boolean sourceAvailable, String message,
                                      List<ConversationDlqMessageVO> records) {
}
