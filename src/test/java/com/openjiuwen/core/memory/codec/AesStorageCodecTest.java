/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.codec;

import com.openjiuwen.core.common.security.AesGcmCrypt;
import com.openjiuwen.core.common.security.BaseCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.common.utils.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the AES storage codec.
 *
 * <p>Mirrors Python's {@code test_aes_storage_codec.py} in
 * {@code tests/unit_tests/core/memory/codec/test_aes_storage_codec.py}.</p>
 */
class AesStorageCodecTest {

    private static final byte[] VALID_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final byte[] OTHER_KEY = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    private static final HexFormat HEX = HexFormat.of();

    @BeforeEach
    void resetGlobalCryptRegistry() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
    }

    @Test
    void encodeDecodeRoundTrip() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);
        String plaintext = "Hello, Memory!";

        String encrypted = codec.encode(plaintext);
        String decrypted = codec.decode(encrypted);

        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encodeDecodeUnicode() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);
        String plaintext = "中文测试 🎉 émojis";

        String encrypted = codec.encode(plaintext);

        assertEquals(plaintext, codec.decode(encrypted));
    }

    @Test
    void encodeLongText() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);
        String plaintext = "A".repeat(10_000);

        String encrypted = codec.encode(plaintext);

        assertEquals(plaintext, codec.decode(encrypted));
    }

    @Test
    void encodeWithoutKey() {
        AesStorageCodec codec = new AesStorageCodec(new byte[0]);

        assertEquals("visible data", codec.encode("visible data"));
    }

    @Test
    void decodeWithoutKey() {
        AesStorageCodec codec = new AesStorageCodec(new byte[0]);

        assertEquals("some ciphertext", codec.decode("some ciphertext"));
    }

    @Test
    void encodeEmptyString() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("", codec.encode(""));
    }

    @Test
    void decodeEmptyString() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("", codec.decode(""));
    }

    @Test
    void encodeWithoutCryptRegistered() {
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("fallback test", codec.encode("fallback test"));
    }

    @Test
    void decodeWithoutCryptRegistered() {
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("some ciphertext", codec.decode("some ciphertext"));
    }

    @Test
    void differentKeysAreIncompatible() {
        registerAesGcm();
        AesStorageCodec codecA = new AesStorageCodec(VALID_KEY);
        AesStorageCodec codecB = new AesStorageCodec(OTHER_KEY);
        String encrypted = codecA.encode("secret message");

        assertEquals(encrypted, codecB.decode(encrypted));
    }

    @Test
    void encodeProducesDifferentOutput() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        String encryptedOne = codec.encode("same text");
        String encryptedTwo = codec.encode("same text");

        assertNotEquals(encryptedOne, encryptedTwo);
        assertEquals("same text", codec.decode(encryptedOne));
        assertEquals("same text", codec.decode(encryptedTwo));
    }

    @Test
    void encodeOutputIsHexString() {
        registerAesGcm();
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        String encrypted = codec.encode("test");

        assertTrue(encrypted.chars().allMatch(character -> {
            char value = (char) character;
            return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f');
        }));
        assertEquals(encrypted, HEX.formatHex(HEX.parseHex(encrypted)));
    }

    @Test
    void encodeEncryptExceptionFallsBackToPlaintext() {
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, new ThrowingCrypt(true, false));
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("fallback on error", codec.encode("fallback on error"));
    }

    @Test
    void decodeDecryptExceptionFallsBackToCiphertext() {
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, new ThrowingCrypt(false, true));
        AesStorageCodec codec = new AesStorageCodec(VALID_KEY);

        assertEquals("some hex ciphertext", codec.decode("some hex ciphertext"));
    }

    private static AesGcmCrypt registerAesGcm() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, crypt);
        return crypt;
    }

    private static final class ThrowingCrypt extends BaseCrypt {

        private final boolean failEncrypt;
        private final boolean failDecrypt;

        private ThrowingCrypt(boolean failEncrypt, boolean failDecrypt) {
            this.failEncrypt = failEncrypt;
            this.failDecrypt = failDecrypt;
        }

        @Override
        public String encrypt(byte[] key, String origin) {
            if (failEncrypt) {
                throw new RuntimeException("encrypt failure");
            }
            return origin;
        }

        @Override
        public String decrypt(byte[] key, String encryptStr) {
            if (failDecrypt) {
                throw new RuntimeException("decrypt failure");
            }
            return encryptStr;
        }
    }
}
