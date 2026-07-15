package com.lingxi.workbench.admin.common;

import com.lingxi.workbench.admin.auth.controller.AdminAccountController;
import com.lingxi.workbench.admin.auth.controller.AdminLoginController;
import com.lingxi.workbench.admin.auth.controller.AdminSessionController;
import com.lingxi.workbench.admin.department.controller.AdminDepartmentController;
import com.lingxi.workbench.admin.menu.controller.AdminMenuController;
import com.lingxi.workbench.admin.role.controller.AdminAccountRoleController;
import com.lingxi.workbench.admin.role.controller.AdminRoleController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端接口命名约束测试。
 *
 * <p>管理端访问日志需要仅通过 URL 即可识别操作，因此每个接口必须声明动作路径，
 * 修改类操作统一使用 POST，不再通过 PUT、DELETE 或 PATCH 区分语义。</p>
 */
class AdminEndpointNamingTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AdminLoginController.class,
            AdminSessionController.class,
            AdminAccountController.class,
            AdminAccountRoleController.class,
            AdminRoleController.class,
            AdminMenuController.class,
            AdminDepartmentController.class
    );

    @Test
    void allEndpointsShouldDeclareReadableActionPaths() {
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(PutMapping.class)).isFalse();
                assertThat(method.isAnnotationPresent(DeleteMapping.class)).isFalse();
                assertThat(method.isAnnotationPresent(PatchMapping.class)).isFalse();

                GetMapping getMapping = method.getAnnotation(GetMapping.class);
                PostMapping postMapping = method.getAnnotation(PostMapping.class);
                if (getMapping != null) {
                    assertThat(getMapping.value()).as("%s#%s", controller.getSimpleName(), method.getName())
                            .isNotEmpty()
                            .allMatch(path -> !path.isBlank());
                }
                if (postMapping != null) {
                    assertThat(postMapping.value()).as("%s#%s", controller.getSimpleName(), method.getName())
                            .isNotEmpty()
                            .allMatch(path -> !path.isBlank());
                }
            }
        }
    }
}
