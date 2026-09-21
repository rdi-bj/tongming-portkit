package com.jinw.web.service;

import com.jinw.web.domain.SysUser;
import com.jinw.web.domain.request.LoginRequest;
import com.jinw.web.domain.response.LoginResponse;

public interface SysUserService {

    LoginResponse login(LoginRequest request);
}