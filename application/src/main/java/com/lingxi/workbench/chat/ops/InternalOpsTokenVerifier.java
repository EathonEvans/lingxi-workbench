package com.lingxi.workbench.chat.ops;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.chat.ops.config.ConversationOpsProperties;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 内部运维接口使用独立共享密钥，不复用用户端登录态。 */
@Component
@RequiredArgsConstructor
public class InternalOpsTokenVerifier {

    private final ConversationOpsProperties properties;

    public void verify(String providedToken) {
        if (CharSequenceUtil.isBlank(properties.getToken())) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "内部运维接口未配置");
        }
        byte[] expected = properties.getToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = CharSequenceUtil.nullToEmpty(providedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(HttpStatusEnum.UNAUTHORIZED, "内部运维凭据无效");
        }
    }
}
