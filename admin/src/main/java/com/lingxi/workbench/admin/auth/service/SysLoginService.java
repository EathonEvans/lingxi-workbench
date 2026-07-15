package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;

public interface SysLoginService {

    SysLoginVO login(SysLoginRequest request);
}
