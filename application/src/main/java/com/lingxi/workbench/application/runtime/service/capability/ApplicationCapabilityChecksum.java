package com.lingxi.workbench.application.runtime.service.capability;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 复算Application能力发布时使用的配置校验和。 */
@Component
public class ApplicationCapabilityChecksum {

    public String calculate(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(JSON.toJSONBytes(value));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "能力配置校验失败");
        }
    }
}
