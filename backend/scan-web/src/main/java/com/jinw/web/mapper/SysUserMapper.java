package com.jinw.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinw.web.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}