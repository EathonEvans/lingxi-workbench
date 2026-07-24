package com.lingxi.workbench.framework.security;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 使用 AES-256-GCM 加解密需要在服务端还原的敏感配置。
 *
 * <p>主密钥只能来自部署环境，不允许写入数据库、日志或接口响应。</p>
 */
@Component
public class SecretCipher {

    private static final int AES_KEY_LENGTH = 32;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final String encodedMasterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${lingxi.security.secret-master-key:}") String encodedMasterKey) {
        this.encodedMasterKey = encodedMasterKey;
    }

    /**
     * 每次加密生成独立随机 IV，相同明文不会产生相同密文。
     */
    public EncryptedSecret encrypt(String secret) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(encode(ciphertext), encode(iv));
        } catch (Exception exception) {
            throw new IllegalStateException("敏感配置加密失败", exception);
        }
    }

    /**
     * 解密仅供服务端运行时使用，明文禁止进入日志、缓存和接口响应。
     */
    public String decrypt(String ciphertext, String encodedIv) {
        try {
            Cipher cipher = createCipher(
                    Cipher.DECRYPT_MODE, Base64.getDecoder().decode(encodedIv));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("敏感配置解密失败", exception);
        }
    }

    /**
     * 创建 AES-256-GCM Cipher 实例，传入主密钥和 IV 完成初始化。
     */
    private Cipher createCipher(int mode, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                mode,
                new SecretKeySpec(decodeMasterKey(encodedMasterKey), "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher;
    }

    /**
     * 解码并校验主密钥，要求 Base64 编码且长度恰好为 32 字节。
     */
    private static byte[] decodeMasterKey(String encodedMasterKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey);
            if (key.length != AES_KEY_LENGTH) {
                throw new IllegalArgumentException();
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "敏感配置主密钥必须是 Base64 编码的 32 字节随机值", exception);
        }
    }

    /**
     * 对字节数组进行 Base64 编码。
     */
    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    /**
     * 加密结果，密文和 IV 必须成对保存。
     *
     * @param ciphertext Base64 编码的密文
     * @param iv         Base64 编码的初始化向量
     */
    public record EncryptedSecret(String ciphertext, String iv) {

        /**
         * 禁止日志输出密文和 IV。
         */
        @Override
        public @NonNull String toString() {
            return "EncryptedSecret[ciphertext=******, iv=******]";
        }
    }
}
