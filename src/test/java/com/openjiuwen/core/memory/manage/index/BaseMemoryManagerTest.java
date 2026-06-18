/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.AesGcmCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Focused verification harness for the isolated BaseMemoryManager candidate.
 *
 * <p>Mirrors Python's helper behavior in
 * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
 */
public final class BaseMemoryManagerTest {

    private BaseMemoryManagerTest() {
    }

    public static void main(String[] args) {
        TestMemoryManager manager = new TestMemoryManager();
        byte[] validKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

        manager.callValidate("user", "scope", new Object(), StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "fragment");
        expectBaseError(
                () -> manager.callValidate("", "scope", new Object(), StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "fragment"),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "user_id is required"
        );
        expectBaseError(
                () -> manager.callValidate("user", "", new Object(), StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "fragment"),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "scope_id is required"
        );
        expectBaseError(
                () -> manager.callValidate("user", "scope", List.of(), StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "fragment"),
                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                "memory_index is not initialized"
        );

        BaseError existing = ErrorHelper.buildError(
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                "memory_type", "fragment",
                "error_msg", "existing"
        );
        try {
            manager.callWrap(existing);
            fail("Expected existing BaseError to be rethrown");
        } catch (BaseError error) {
            if (error != existing) {
                fail("Expected same BaseError instance to be rethrown");
            }
        }

        IllegalStateException cause = new IllegalStateException("boom");
        try {
            manager.callWrap(cause);
            fail("Expected non-BaseError exception to be wrapped");
        } catch (BaseError error) {
            assertEquals(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, error.getStatus(), "wrapped status");
            assertEquals("fragment", error.getParams().get("memory_type"), "wrapped memory_type");
            assertEquals("boom", error.getParams().get("error_msg"), "wrapped error_msg");
            if (error.getCause() != cause) {
                fail("Expected wrapped cause to be preserved");
            }
        }

        String plaintext = "Hello, Memory!";
        String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(validKey, plaintext);
        if (plaintext.equals(encrypted)) {
            fail("Expected valid AES-GCM encryption to change plaintext");
        }
        assertEquals(plaintext, BaseMemoryManager.decryptMemoryIfNeeded(validKey, encrypted),
                "valid AES-GCM round trip");

        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        assertEquals(plaintext, BaseMemoryManager.encryptMemoryIfNeeded(validKey, plaintext),
                "missing crypt returns plaintext");
        assertEquals(encrypted, BaseMemoryManager.decryptMemoryIfNeeded(validKey, encrypted),
                "missing crypt returns ciphertext");
        AesGcmCrypt.getInstance();

        assertEquals("plain", BaseMemoryManager.encryptMemoryIfNeeded(null, "plain"), "null key returns plaintext");
        assertEquals("", BaseMemoryManager.encryptMemoryIfNeeded(new byte[]{1, 2}, ""), "empty plaintext unchanged");
        assertEquals("plain", BaseMemoryManager.encryptMemoryIfNeeded(new byte[]{1, 2}, "plain"),
                "encrypt failure returns plaintext");
        assertEquals("cipher", BaseMemoryManager.decryptMemoryIfNeeded(new byte[]{1, 2}, "cipher"),
                "decrypt failure returns ciphertext");
        System.out.println("PASS BaseMemoryManagerTest");
    }

    private static void expectBaseError(CheckedRunnable action, StatusCode statusCode, String errorMessage) {
        try {
            action.run();
            fail("Expected BaseError");
        } catch (BaseError error) {
            assertEquals(statusCode, error.getStatus(), "status");
            assertEquals("fragment", error.getParams().get("memory_type"), "memory_type");
            assertEquals(errorMessage, error.getParams().get("error_msg"), "error_msg");
        } catch (Exception exception) {
            throw new AssertionError("Expected BaseError, got " + exception.getClass().getName(), exception);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class TestMemoryManager extends BaseMemoryManager {
        void callValidate(String userId, String scopeId, Object memoryIndex, StatusCode statusCode,
                          String memoryType) {
            validateRequiredParams(userId, scopeId, memoryIndex, statusCode, memoryType);
        }

        void callWrap(Throwable exception) {
            wrapException(exception, StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR, "fragment");
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(String userId, String scopeId,
                                                                 Map<String, List<BaseMemoryUnit>> memories,
                                                                 Model llm, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Boolean> update(String userId, String scopeId, String memId, String newMemory,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Boolean> delete(String userId, String scopeId, String memId,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletionStage<List<Map<String, Object>>> search(String userId, String scopeId, String query,
                                                                 int topK, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
