/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.security.AesGcmCrypt;
import com.openjiuwen.core.common.security.BaseCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.common.utils.Singleton;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.core.memory.test_base_memory_manager_crypto} in
 * {@code tests/unit_tests/core/memory/test_base_memory_manager_crypto.py}.
 */
class BaseMemoryManagerCryptoTest {

    private static final byte[] VALID_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final byte[] OTHER_KEY = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void resetGlobalCryptRegistry() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
    }

    @Test
    void encryptDecryptViaCrypt() {
        registerAesGcm();
        String plaintext = "Hello, Memory!";

        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, plaintext);

        assertNotEquals(plaintext, encrypted);
        assertTrue(encrypted.chars().allMatch(BaseMemoryManagerCryptoTest::isLowerHex));
        assertEquals(plaintext, BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));
    }

    @Test
    void encryptReturnsPlaintextWithoutKey() {
        assertEquals("visible data", BaseMemoryManager.encryptMemoryIfNeeded(new byte[0], "visible data"));
    }

    @Test
    void decryptReturnsCiphertextWithoutKey() {
        assertEquals("some ciphertext", BaseMemoryManager.decryptMemoryIfNeeded(new byte[0], "some ciphertext"));
    }

    @Test
    void encryptEmptyString() {
        registerAesGcm();

        assertEquals("", BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, ""));
    }

    @Test
    void decryptEmptyString() {
        assertEquals("", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, ""));
    }

    @Test
    void encryptReturnsPlaintextWithoutCrypt() {
        assertEquals("fallback test", BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "fallback test"));
    }

    @Test
    void decryptReturnsCiphertextWithoutCrypt() {
        registerAesGcm();
        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "test data");
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);

        assertEquals(encrypted, BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));
    }

    @Test
    void encryptUnicode() {
        registerAesGcm();
        String plaintext = "\u4e2d\u6587\u6d4b\u8bd5 \ud83c\udf80 \u00e9mojis";

        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, plaintext);

        assertEquals(plaintext, BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));
    }

    @Test
    void decryptLegacyCiphertextWithCryptRegistered() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        String legacyCiphertext = crypt.encrypt(VALID_KEY, "legacy data");
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, crypt);

        assertEquals("legacy data", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, legacyCiphertext));
    }

    @Test
    void encryptNoKeyNoCrypt() {
        assertEquals("plaintext", BaseMemoryManager.encryptMemoryIfNeeded(new byte[0], "plaintext"));
    }

    @Test
    void decryptNoKeyNoCrypt() {
        assertEquals("ciphertext", BaseMemoryManager.decryptMemoryIfNeeded(new byte[0], "ciphertext"));
    }

    @Test
    void encryptDecryptWithDifferentKeysFails() {
        AesGcmCrypt crypt = registerAesGcm();
        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "secret");

        assertThrows(Exception.class, () -> crypt.decrypt(OTHER_KEY, encrypted));
    }

    @Test
    void encryptExceptionReturnsPlaintext() {
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, new ThrowingCrypt(true, false));

        assertEquals("safe plaintext", BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "safe plaintext"));
    }

    @Test
    void decryptExceptionReturnsCiphertext() {
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, new ThrowingCrypt(false, true));
        String ciphertext = "aabbccdd".repeat(10);

        assertEquals(ciphertext, BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, ciphertext));
    }

    @Test
    void fullEncryptionWorkflow() {
        AesGcmCrypt crypt = registerAesGcm();
        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "important memory data");

        assertNotEquals("important memory data", encrypted);
        assertEquals("important memory data", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));

        String rawCipher = crypt.encrypt(VALID_KEY, "config secret");
        assertEquals("config secret", crypt.decrypt(VALID_KEY, rawCipher));
    }

    @Test
    void keyBasedToggle() {
        registerAesGcm();
        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "toggle test");

        assertNotEquals("toggle test", encrypted);
        assertEquals("toggle test", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));
        assertEquals("new data", BaseMemoryManager.encryptMemoryIfNeeded(new byte[0], "new data"));
        assertEquals("toggle test", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, encrypted));

        String newEncrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "more data");
        assertEquals("more data", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, newEncrypted));
    }

    @Test
    void legacyDataCompatibility() {
        AesGcmCrypt crypt = registerAesGcm();
        String legacyCiphertext = crypt.encrypt(VALID_KEY, "old format data");

        assertEquals("old format data", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, legacyCiphertext));

        String newEncrypted = BaseMemoryManager.encryptMemoryIfNeeded(VALID_KEY, "new format data");
        assertEquals("new format data", BaseMemoryManager.decryptMemoryIfNeeded(VALID_KEY, newEncrypted));
        assertNotEquals("new format data", newEncrypted);
        assertTrue(!legacyCiphertext.startsWith("{\""));
    }

    private static AesGcmCrypt registerAesGcm() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, crypt);
        return crypt;
    }

    private static boolean isLowerHex(int character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
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
                throw new RuntimeException("encrypt boom");
            }
            return origin;
        }

        @Override
        public String decrypt(byte[] key, String encryptStr) {
            if (failDecrypt) {
                throw new RuntimeException("decrypt boom");
            }
            return encryptStr;
        }
    }
}
