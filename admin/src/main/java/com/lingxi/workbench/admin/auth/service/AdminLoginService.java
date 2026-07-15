package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.request.AdminLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminLoginVO;

public interface AdminLoginService {

    AdminLoginVO login(AdminLoginRequest request);
}
