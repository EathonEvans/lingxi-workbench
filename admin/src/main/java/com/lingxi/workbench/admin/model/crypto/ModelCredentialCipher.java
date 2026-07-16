package com.lingxi.workbench.admin.model.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 使用部署环境提供的主密钥加密模型访问密钥。
 */
@Component
public class ModelCredentialCipher {

    private static final int AES_KEY_LENGTH = 32;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final String encodedMasterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ModelCredentialCipher(@Value("${lingxi.model.credential-master-key:}") String encodedMasterKey) {
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
            throw new IllegalStateException("模型密钥加密失败", exception);
        }
    }

    /**
     * 仅供后续运行时构建模型客户端使用，禁止通过任何管理接口返回解密结果。
     */
    public String decrypt(String ciphertext, String encodedIv) {
        try {
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(encodedIv));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("模型密钥解密失败", exception);
        }
    }

    private Cipher createCipher(int mode, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(decodeMasterKey(encodedMasterKey), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher;
    }

    private static byte[] decodeMasterKey(String encodedMasterKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey);
            if (key.length != AES_KEY_LENGTH) {
                throw new IllegalArgumentException();
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("模型密钥主密钥必须是 Base64 编码的 32 字节随机值", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public record EncryptedSecret(String ciphertext, String iv) {
    }
}
