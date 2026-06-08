/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.Singleton;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/**
 * Mirrors Python's {@code AesGcmCrypt} in
 * {@code openjiuwen/core/common/security/crypt_utils.py}.
 */
public final class AesGcmCrypt extends BaseCrypt {

    static final int NONCE_LENGTH = 12;

    static final int AES_KEY_LENGTH = 32;

    static final int TAG_LENGTH = 16;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final HexFormat HEX = HexFormat.of();

    private AesGcmCrypt() {
    }

    public static AesGcmCrypt getInstance() {
        AesGcmCrypt instance = Singleton.getInstance(AesGcmCrypt.class, AesGcmCrypt::new);
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, instance);
        return instance;
    }

    static void validateKey(byte[] key) {
        if (key == null || key.length != AES_KEY_LENGTH) {
            int actualLength = key == null ? 0 : key.length;
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_ENCRYPTION_ERROR,
                    null,
                    null,
                    null,
                    Map.of("error_msg", "Key must be " + AES_KEY_LENGTH + " bytes, got " + actualLength)
            );
        }
    }

    @Override
    public String encrypt(byte[] key, String origin) {
        validateKey(key);
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, nonce));
            byte[] encryptedWithTag = cipher.doFinal(origin.getBytes(StandardCharsets.UTF_8));
            int cipherTextLength = encryptedWithTag.length - TAG_LENGTH;
            byte[] cipherText = new byte[cipherTextLength];
            byte[] tag = new byte[TAG_LENGTH];
            System.arraycopy(encryptedWithTag, 0, cipherText, 0, cipherTextLength);
            System.arraycopy(encryptedWithTag, cipherTextLength, tag, 0, TAG_LENGTH);
            return HEX.formatHex(nonce) + HEX.formatHex(tag) + HEX.formatHex(cipherText);
        } catch (GeneralSecurityException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_ENCRYPTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", exception.getMessage() != null ? exception.getMessage() : "AES-GCM encryption failed")
            );
        }
    }

    @Override
    public String decrypt(byte[] key, String encryptStr) {
        validateKey(key);
        int nonceLengthHex = NONCE_LENGTH * 2;
        int tagLengthHex = TAG_LENGTH * 2;
        int minimumLength = nonceLengthHex + tagLengthHex;
        if (encryptStr == null || encryptStr.length() < minimumLength) {
            int actualLength = encryptStr == null ? 0 : encryptStr.length();
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_DECRYPTION_ERROR,
                    null,
                    null,
                    null,
                    Map.of("error_msg", "Ciphertext too short: expected at least " + minimumLength + " chars, got " + actualLength)
            );
        }

        try {
            String nonceHex = encryptStr.substring(0, nonceLengthHex);
            String tagHex = encryptStr.substring(nonceLengthHex, nonceLengthHex + tagLengthHex);
            String cipherTextHex = encryptStr.substring(minimumLength);
            byte[] nonce = HEX.parseHex(nonceHex);
            byte[] tag = HEX.parseHex(tagHex);
            byte[] cipherText = HEX.parseHex(cipherTextHex);

            if (nonce.length != NONCE_LENGTH) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_DECRYPTION_ERROR,
                        null,
                        null,
                        null,
                        Map.of("error_msg", "Wrong nonce length: " + nonce.length)
                );
            }
            if (tag.length != TAG_LENGTH) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_DECRYPTION_ERROR,
                        null,
                        null,
                        null,
                        Map.of("error_msg", "Wrong tag length: " + tag.length + ", expected " + TAG_LENGTH)
                );
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, nonce));
            byte[] combined = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, combined, 0, cipherText.length);
            System.arraycopy(tag, 0, combined, cipherText.length, tag.length);
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_DECRYPTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", exception.getMessage() != null ? exception.getMessage() : "Invalid ciphertext hex")
            );
        } catch (GeneralSecurityException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_DECRYPTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", exception.getMessage() != null ? exception.getMessage() : "AES-GCM decryption failed")
            );
        }
    }
}
