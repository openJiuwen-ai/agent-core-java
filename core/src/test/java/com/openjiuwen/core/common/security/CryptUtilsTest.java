// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for translated crypt helpers.
 *
 * <p>Mirrors Python's {@code crypt_utils.py} in
 * {@code openjiuwen/core/common/security/crypt_utils.py}.</p>
 * <p>Mirrors Python's crypt utils unit tests in
 * {@code tests/unit_tests/core/common/utils/test_crypt_utils.py}.</p>
 */
class CryptUtilsTest {

    private static final byte[] VALID_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final byte[] OTHER_KEY = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void resetGlobalState() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        AesGcmCrypt.getInstance();
    }

    @Test
    void aesGcmCryptEncryptDecryptRoundTrip() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();

        String plaintext = "hello, world! 你好世界";
        String encrypted = crypt.encrypt(VALID_KEY, plaintext);
        String decrypted = crypt.decrypt(VALID_KEY, encrypted);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void aesGcmCryptEncryptProducesDifferentCiphertext() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();

        String encrypted1 = crypt.encrypt(VALID_KEY, "same text");
        String encrypted2 = crypt.encrypt(VALID_KEY, "same text");

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void aesGcmCryptRejectsWrongKeyLengthOnEncrypt() {
        BaseError error = assertThrows(BaseError.class,
                () -> AesGcmCrypt.getInstance().encrypt("short-key".getBytes(StandardCharsets.UTF_8), "hello"));

        assertEquals(StatusCode.COMMON_ENCRYPTION_ERROR, error.getStatus());
    }

    @Test
    void aesGcmCryptRejectsWrongKeyLengthOnDecrypt() {
        BaseError error = assertThrows(BaseError.class,
                () -> AesGcmCrypt.getInstance().decrypt("short".getBytes(StandardCharsets.UTF_8), "cipher"));

        assertEquals(StatusCode.COMMON_ENCRYPTION_ERROR, error.getStatus());
    }

    @Test
    void aesGcmCryptRejectsGarbageCiphertext() {
        BaseError error = assertThrows(BaseError.class, () -> AesGcmCrypt.getInstance().decrypt(VALID_KEY, "not_valid_ciphertext"));

        assertEquals(StatusCode.COMMON_DECRYPTION_ERROR, error.getStatus());
    }

    @Test
    void aesGcmCryptSupportsEmptyStringRoundTrip() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();

        String encrypted = crypt.encrypt(VALID_KEY, "");

        assertEquals("", crypt.decrypt(VALID_KEY, encrypted));
    }

    @Test
    void aesGcmCryptRejectsTooShortCiphertext() {
        BaseError error = assertThrows(BaseError.class, () -> AesGcmCrypt.getInstance().decrypt(VALID_KEY, "ab"));

        assertEquals(StatusCode.COMMON_DECRYPTION_ERROR, error.getStatus());
        assertEquals("Ciphertext too short: expected at least 56 chars, got 2", error.getParams().get("error_msg"));
    }

    @Test
    void aesGcmCryptRejectsDecryptWithDifferentKey() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        String encrypted = crypt.encrypt(VALID_KEY, "cross key test");

        BaseError error = assertThrows(BaseError.class, () -> crypt.decrypt(OTHER_KEY, encrypted));

        assertEquals(StatusCode.COMMON_DECRYPTION_ERROR, error.getStatus());
    }

    @Test
    void aesGcmCryptDecryptsWithCorrectKey() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        String encrypted = crypt.encrypt(OTHER_KEY, "key specific");

        assertEquals("key specific", crypt.decrypt(OTHER_KEY, encrypted));
    }

    @Test
    void cryptRegistryRegistersAndReturnsValue() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();

        CryptUtils.registerCrypt("test", crypt);

        assertSame(crypt, CryptUtils.getCrypt("test"));
    }

    @Test
    void cryptRegistryRejectsNonBaseCrypt() {
        BaseError error = assertThrows(BaseError.class, () -> CryptUtils.registerCrypt("bad", "not_a_crypt"));

        assertEquals(StatusCode.COMMON_ENCRYPTION_ERROR, error.getStatus());
        assertEquals(Map.of("error_msg", "crypt must be a BaseCrypt instance, got class java.lang.String"), error.getParams());
    }

    @Test
    void cryptRegistryUnregisterRemovesValue() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();

        CryptUtils.registerCrypt("test", crypt);
        CryptUtils.unregisterCrypt("test");

        assertNull(CryptUtils.getCrypt("test"));
    }

    @Test
    void cryptRegistryReturnsNullForUnknownName() {
        assertNull(CryptUtils.getCrypt("nonexistent"));
    }

    @Test
    void cryptRegistryKeepsMultipleRegistrations() {
        AesGcmCrypt crypt1 = AesGcmCrypt.getInstance();
        AesGcmCrypt crypt2 = AesGcmCrypt.getInstance();

        CryptUtils.registerCrypt("c1", crypt1);
        CryptUtils.registerCrypt("c2", crypt2);

        assertSame(crypt1, CryptUtils.getCrypt("c1"));
        assertSame(crypt2, CryptUtils.getCrypt("c2"));
    }

    @Test
    void cryptRegistryOverwriteKeepsLatestValue() {
        DummyCrypt crypt1 = new DummyCrypt("first");
        DummyCrypt crypt2 = new DummyCrypt("second");

        CryptUtils.registerCrypt("same", crypt1);
        CryptUtils.registerCrypt("same", crypt2);

        assertSame(crypt2, CryptUtils.getCrypt("same"));
    }

    @Test
    void cryptRegistryConcurrentRegisterUnregisterIsSafe() throws InterruptedException {
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < 5; index++) {
            Thread registerThread = new Thread(() -> {
                ready.countDown();
                awaitLatch(start, errors);
                try {
                    CryptUtils.registerCrypt("concurrent_test", new DummyCrypt("register"));
                } catch (Throwable throwable) {
                    errors.add(throwable);
                }
            });
            Thread unregisterThread = new Thread(() -> {
                ready.countDown();
                awaitLatch(start, errors);
                try {
                    CryptUtils.unregisterCrypt("concurrent_test");
                } catch (Throwable throwable) {
                    errors.add(throwable);
                }
            });
            threads.add(registerThread);
            threads.add(unregisterThread);
        }

        for (Thread thread : threads) {
            thread.start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(errors.isEmpty());
        BaseCrypt result = CryptUtils.getCrypt("concurrent_test");
        assertTrue(result == null || result instanceof BaseCrypt);
    }

    @Test
    void aesGcmCryptSingletonConcurrentCreationReturnsSameInstance() throws InterruptedException {
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<AesGcmCrypt> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < 10; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                awaitLatch(start, errors);
                try {
                    results.add(AesGcmCrypt.getInstance());
                } catch (Throwable throwable) {
                    errors.add(throwable);
                }
            });
            threads.add(thread);
        }

        for (Thread thread : threads) {
            thread.start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(errors.isEmpty());
        assertFalse(results.isEmpty());
        AesGcmCrypt first = results.get(0);
        for (AesGcmCrypt result : results) {
            assertSame(first, result);
        }
    }

    @Test
    void cryptRegistryInitializesAesGcmOnSingletonCreation() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);

        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        BaseCrypt registered = CryptUtils.getCrypt(CryptUtils.AES_GCM_CRYPT_NAME);

        assertNotNull(registered);
        assertNotNull(crypt);
        assertSame(crypt, registered);
    }

    @Test
    void decryptRejectsTamperedCiphertext() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        String encrypted = crypt.encrypt(VALID_KEY, "hello world");
        // Flip one nibble inside the GCM tag (after 12-byte nonce hex) so AEAD must fail.
        // Appending/replacing the last ciphertext chars can be a no-op when they already match.
        int tagNibble = AesGcmCrypt.NONCE_LENGTH * 2;
        StringBuilder tampered = new StringBuilder(encrypted);
        char original = tampered.charAt(tagNibble);
        tampered.setCharAt(tagNibble, original == '0' ? '1' : '0');

        BaseError error = assertThrows(BaseError.class, () -> crypt.decrypt(VALID_KEY, tampered.toString()));

        assertEquals(StatusCode.COMMON_DECRYPTION_ERROR, error.getStatus());
    }

    private static void awaitLatch(CountDownLatch latch, List<Throwable> errors) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            errors.add(exception);
        }
    }

    private static final class DummyCrypt extends BaseCrypt {

        private final String marker;

        private DummyCrypt(String marker) {
            this.marker = marker;
        }

        @Override
        public String encrypt(byte[] key, String origin) {
            return marker + ":" + origin;
        }

        @Override
        public String decrypt(byte[] key, String encryptStr) {
            return encryptStr;
        }
    }
}
