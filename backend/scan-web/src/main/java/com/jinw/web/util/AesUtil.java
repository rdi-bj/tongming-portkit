package com.jinw.web.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-128-CBC 加解密工具类。
 *
 * <p>规则表（b_t_arch_keyword 等）中的知识库内容字段在 SQL 中以密文存储，
 * 运行时通过 {@link #decrypt(String)} 解密后再使用。密钥与初始向量由
 * {@code security.aes.key} / {@code security.aes.iv} 配置注入，初始化于
 * {@link com.jinw.web.config.AesConfig}。
 *
 * <p>注意：AES-128 要求密钥与 IV 均为 16 字节（UTF-8）。
 */
public final class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private static volatile SecretKeySpec keySpec;
    private static volatile IvParameterSpec ivSpec;

    private AesUtil() {
    }

    /**
     * 初始化密钥与 IV，须在应用启动早期调用一次。
     *
     * @param key 16 字节（UTF-8）密钥
     * @param iv  16 字节（UTF-8）初始向量
     */
    public static synchronized void init(String key, String iv) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            throw new IllegalArgumentException(
                    "AES-128 key 必须为 16 字节(UTF-8)，当前长度: " + keyBytes.length);
        }
        if (ivBytes.length != 16) {
            throw new IllegalArgumentException(
                    "AES-128 IV 必须为 16 字节(UTF-8)，当前长度: " + ivBytes.length);
        }
        keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        ivSpec = new IvParameterSpec(ivBytes);
    }

    /**
     * 加密明文，返回 Base64 编码的密文。null 返回 null。
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        ensureInitialized();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密 Base64 编码的密文，返回明文。null 返回 null。
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        ensureInitialized();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    private static void ensureInitialized() {
        if (keySpec == null || ivSpec == null) {
            throw new IllegalStateException("AesUtil 尚未初始化，请检查 security.aes.key / security.aes.iv 配置");
        }
    }
}
