package com.jinw.web.domain.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginResponse {

    @Schema(description = "用户ID")
    private String id;

    @Schema(description = "用户账号")
    private String loginName;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "用户类型")
    private String userType;

    @Schema(description = "令牌")
    private String token;
}