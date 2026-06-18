/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.security.BaseCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Simplified abstract base class for memory manager implementations.
 * Managing a specific type of memory data.
 *
 * <p>Mirrors Python's {@code BaseMemoryManager} in
 * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
 */
public abstract class BaseMemoryManager {

    protected static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private static final String MEMORY_TYPE_KEY = "memory_type";
    private static final String ERROR_MSG_KEY = "error_msg";
    private static final String USER_ID_REQUIRED = "user_id is required";
    private static final String SCOPE_ID_REQUIRED = "scope_id is required";
    private static final String MEMORY_INDEX_REQUIRED = "memory_index is not initialized";

    /**
     * Add memories in batch.
     *
     * <p>Mirrors Python's {@code add_memories(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<List<BaseMemoryUnit>> addMemories(
            String userId,
            String scopeId,
            Map<String, List<BaseMemoryUnit>> memories,
            Model llm,
            Map<String, Object> kwargs
    );

    public CompletionStage<List<BaseMemoryUnit>> addMemories(
            String userId,
            String scopeId,
            Map<String, List<BaseMemoryUnit>> memories,
            Model llm
    ) {
        return addMemories(userId, scopeId, memories, llm, Map.of());
    }

    /**
     * Update memory by its id.
     *
     * <p>Mirrors Python's {@code update(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<Boolean> update(
            String userId,
            String scopeId,
            String memId,
            String newMemory,
            Map<String, Object> kwargs
    );

    public CompletionStage<Boolean> update(String userId, String scopeId, String memId, String newMemory) {
        return update(userId, scopeId, memId, newMemory, Map.of());
    }

    /**
     * Delete memory by its id.
     *
     * <p>Mirrors Python's {@code delete(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<Boolean> delete(
            String userId,
            String scopeId,
            String memId,
            Map<String, Object> kwargs
    );

    public CompletionStage<Boolean> delete(String userId, String scopeId, String memId) {
        return delete(userId, scopeId, memId, Map.of());
    }

    /**
     * Delete memory by user id and app id.
     *
     * <p>Mirrors Python's {@code delete_by_user_id(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<Boolean> deleteByUserId(
            String userId,
            String scopeId,
            Map<String, Object> kwargs
    );

    public CompletionStage<Boolean> deleteByUserId(String userId, String scopeId) {
        return deleteByUserId(userId, scopeId, Map.of());
    }

    /**
     * Get memory by its id.
     *
     * <p>Mirrors Python's {@code get(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<Map<String, Object>> get(String userId, String scopeId, String memId);

    /**
     * Query memory, return top k results.
     *
     * <p>Mirrors Python's {@code search(...)} coroutine in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    public abstract CompletionStage<List<Map<String, Object>>> search(
            String userId,
            String scopeId,
            String query,
            int topK,
            Map<String, Object> kwargs
    );

    public CompletionStage<List<Map<String, Object>>> search(String userId, String scopeId, String query, int topK) {
        return search(userId, scopeId, query, topK, Map.of());
    }

    /**
     * Validate required parameters for memory operations.
     *
     * <p>Mirrors Python's {@code _validate_required_params(...)} in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    protected void validateRequiredParams(
            String userId,
            String scopeId,
            Object memoryIndex,
            StatusCode statusCode,
            String memoryType
    ) {
        if (userId == null || userId.isEmpty()) {
            throw buildMemoryError(statusCode, memoryType, USER_ID_REQUIRED);
        }
        if (scopeId == null || scopeId.isEmpty()) {
            throw buildMemoryError(statusCode, memoryType, SCOPE_ID_REQUIRED);
        }
        if (isPythonFalsy(memoryIndex)) {
            throw buildMemoryError(statusCode, memoryType, MEMORY_INDEX_REQUIRED);
        }
    }

    /**
     * Wrap exception into unified BaseError.
     * If the exception is already a BaseError, re-raise it directly.
     *
     * <p>Mirrors Python's {@code _wrap_exception(...)} in
     * {@code openjiuwen/core/memory/manage/index/base_memory_manager.py}.</p>
     */
    protected void wrapException(Throwable exception, StatusCode statusCode, String memoryType) {
        if (exception instanceof BaseError baseError) {
            throw baseError;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(MEMORY_TYPE_KEY, memoryType);
        params.put(ERROR_MSG_KEY, exceptionMessage(exception));
        ErrorHelper.raiseError(statusCode, null, null, exception, params);
    }

    public static String encryptMemoryIfNeeded(byte[] key, String plaintext) {
        if (key == null || key.length == 0 || plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        BaseCrypt crypt = CryptUtils.getCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        if (crypt == null) {
            return plaintext;
        }

        try {
            return crypt.encrypt(key, plaintext);
        } catch (Exception exception) {
            MEMORY_LOGGER.warning(
                    "Encrypt error via crypt",
                    "exception", exceptionMessage(exception),
                    "event_type", LogEventType.MEMORY_PROCESS
            );
            return plaintext;
        }
    }

    public static String decryptMemoryIfNeeded(byte[] key, String ciphertext) {
        if (key == null || key.length == 0 || ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        BaseCrypt crypt = CryptUtils.getCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        if (crypt == null) {
            return ciphertext;
        }

        try {
            return crypt.decrypt(key, ciphertext);
        } catch (Exception exception) {
            MEMORY_LOGGER.warning(
                    "Decrypt error via crypt",
                    "exception", exceptionMessage(exception),
                    "event_type", LogEventType.MEMORY_PROCESS
            );
            return ciphertext;
        }
    }

    private static BaseError buildMemoryError(StatusCode statusCode, String memoryType, String errorMessage) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(MEMORY_TYPE_KEY, memoryType);
        params.put(ERROR_MSG_KEY, errorMessage);
        return ErrorHelper.buildError(statusCode, null, null, null, params);
    }

    private static boolean isPythonFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean boolValue) {
            return !boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() == 0.0d;
        }
        if (value instanceof CharSequence charSequence) {
            return charSequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof Optional<?> optional) {
            return optional.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0;
        }
        return false;
    }

    private static String exceptionMessage(Throwable exception) {
        if (exception == null) {
            return "null";
        }
        String message = exception.getMessage();
        return message == null ? "" : message;
    }
}
