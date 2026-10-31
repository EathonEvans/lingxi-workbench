package com.lingxi.workbench.notification.model.converter;

import com.lingxi.workbench.notification.model.entity.NotificationEntity;
import com.lingxi.workbench.notification.model.vo.NotificationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.time.OffsetDateTime;

/** 站内通知模型转换器。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationConverter {

    /** 转换通知响应，Long 主键由 MapStruct 转换为字符串。 */
    @Mapping(target = "read", source = "readAt", qualifiedByName = "toReadFlag")
    NotificationVO toVO(NotificationEntity entity);

    /** 阅读时间存在即视为已读。 */
    @Named("toReadFlag")
    default Boolean toReadFlag(OffsetDateTime readAt) {
        return readAt != null;
    }
}
