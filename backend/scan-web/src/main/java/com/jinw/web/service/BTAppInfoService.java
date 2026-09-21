package com.jinw.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinw.web.domain.BTAppInfo;

public interface BTAppInfoService extends IService<BTAppInfo> {

    String create(BTAppInfo appInfo);

    String deleteById(String id);

    String update(BTAppInfo appInfo);

    BTAppInfo getById(String id);
}