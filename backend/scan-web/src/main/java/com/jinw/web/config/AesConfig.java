package com.jinw.web.config;

import com.jinw.web.util.AesUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 初始化规则表字段加解密所需的 AES 密钥与 IV。
 *
 * <p>密钥从 {@code security.aes.key} / {@code security.aes.iv} 读取，请勿将真实
 * 密钥提交到版本库。
 */
@Component("aesConfig")
public class AesConfig {

    @Value("${security.aes.key}")
    private String key;

    @Value("${security.aes.iv}")
    private String iv;

    @PostConstruct
    public void init() {
        AesUtil.init(key, iv);
    }
}
