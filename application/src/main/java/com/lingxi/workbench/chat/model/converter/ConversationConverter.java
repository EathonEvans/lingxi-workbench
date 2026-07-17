package com.lingxi.workbench.chat.model.converter;

import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.vo.ConversationMessageVO;
import com.lingxi.workbench.chat.model.vo.ConversationVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/** 会话及消息模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ConversationConverter {
    /** 转换会话响应，Long 主键由 MapStruct 转换为字符串。 */
    ConversationVO toVO(ConversationEntity entity);

    /** 转换消息响应，Long 主键由 MapStruct 转换为字符串。 */
    ConversationMessageVO toMessageVO(ConversationMessageEntity entity);
}
