package com.jinw.web.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.jinw.web.base.RestResult;
import com.jinw.web.domain.request.LoginRequest;
import com.jinw.web.domain.response.LoginResponse;
import com.jinw.web.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys/user")
@Tag(name = "用户接口")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    @ApiOperationSupport(order = 10)
    public RestResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return RestResult.success(sysUserService.login(request));
    }
}