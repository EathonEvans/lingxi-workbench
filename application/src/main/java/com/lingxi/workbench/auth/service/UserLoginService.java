package com.lingxi.workbench.auth.service;

import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;

public interface UserLoginService {

    UserLoginVO login(UserLoginRequest request);
}
