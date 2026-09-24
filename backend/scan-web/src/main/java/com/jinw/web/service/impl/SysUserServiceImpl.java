package com.jinw.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinw.web.domain.SysUser;
import com.jinw.web.domain.request.LoginRequest;
import com.jinw.web.domain.response.LoginResponse;
import com.jinw.web.mapper.SysUserMapper;
import com.jinw.web.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getLoginName, request.getLoginName())
        );

        if (user == null) {
            throw new com.jinw.web.config.exception.BusinessException("用户不存在");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            throw new com.jinw.web.config.exception.BusinessException("密码错误");
        }

        if (!"0".equals(user.getStatus())) {
            throw new com.jinw.web.config.exception.BusinessException("账号已锁定或停用");
        }

        String token = UUID.randomUUID().toString().replace("-", "");

        LoginResponse response = new LoginResponse();
        response.setId(user.getId());
        response.setLoginName(user.getLoginName());
        response.setUserName(user.getUserName());
        response.setUserType(user.getUserType());
        response.setToken(token);

        return response;
    }
}