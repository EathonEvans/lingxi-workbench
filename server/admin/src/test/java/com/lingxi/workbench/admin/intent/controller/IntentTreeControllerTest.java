package com.lingxi.workbench.admin.intent.controller;

import com.lingxi.workbench.admin.intent.model.request.PublishIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionDetailVO;
import com.lingxi.workbench.admin.intent.service.IntentTreeService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentTreeControllerTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void shouldUseCurrentOperatorWhenPublishingIntentTree() {
        UserContext.set(new LoginUser(7L, "tester", "测试用户", null));
        PublishIntentTreeRequest request = new PublishIntentTreeRequest();
        request.setExpectedRevision(3L);
        IntentTreeVersionDetailVO version = IntentTreeVersionDetailVO.builder().id("21").versionNo(2).build();
        AtomicReference<Long> actualOperatorId = new AtomicReference<>();
        IntentTreeService service = (IntentTreeService) Proxy.newProxyInstance(
                IntentTreeService.class.getClassLoader(),
                new Class<?>[]{IntentTreeService.class},
                (proxy, method, arguments) -> {
                    if ("publish".equals(method.getName())) {
                        actualOperatorId.set((Long) arguments[2]);
                        return version;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        IntentTreeController controller = new IntentTreeController(service);

        ApiResponse<IntentTreeVersionDetailVO> response = controller.publish(10L, request);

        assertEquals(200, response.code());
        assertEquals(version, response.data());
        assertEquals(7L, actualOperatorId.get());
    }
}
