package com.jinw.web.domain.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户账号")
    private String loginName;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "用户密码")
    private String password;
}