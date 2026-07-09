/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.MemoryCrypto;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for memory manager implementations.
 * Each subclass manages a specific type of memory data.
 * 
 * @since 0.1.7
 */
public abstract class BaseMemoryManager {
    /**
     * MEMORY_LOGGER.
     * 
     * @since 0.1.7
     */
    protected static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    /**
     * NONCE_HEX_LENGTH.
     * 
     * @since 0.1.7
     */
    protected static final int NONCE_HEX_LENGTH = MemoryCrypto.NONCE_LENGTH * 2;

    /**
     * TAG_HEX_LENGTH.
     * 
     * @since 0.1.7
     */
    protected static final int TAG_HEX_LENGTH = MemoryCrypto.TAG_LENGTH * 2;

    /**
     * Add memories in batch.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memories memories
     * @param llm llm
     * @param kwargs kwargs
     * @since 0.1.7
     */
    public abstract void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories,
            Map.Entry<String, Model> llm, Map<String, Object> kwargs);

    /**
     * Update memory by its id.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param newMemory newMemory
     * @param kwargs kwargs
     * @since 0.1.7
     */
    public abstract void update(String userId, String scopeId, String memId, String newMemory,
            Map<String, Object> kwargs);

    /**
     * Delete memory by its id.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public abstract boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs);

    /**
     * Delete memory by user id and scope id.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public abstract boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs);

    /**
     * Get memory by its id.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param memId memId
     * @return the result
     * @since 0.1.7
     */
    public abstract Map<String, Object> get(String userId, String scopeId, String memId);

    /**
     * Query memory, return top k results.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @param query query
     * @param topK topK
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public abstract List<Map<String, Object>> search(String userId, String scopeId, String query, int topK,
            Map<String, Object> kwargs);

    /**
     * Encrypt plaintext if a valid crypto key is provided.
     * Format: nonce_hex + tag_hex + ciphertext_hex
     * 
     * @param key key
     * @param plaintext plaintext
     * @return the result
     * @since 0.1.7
     */
    public static String encryptMemoryIfNeeded(byte[] key, String plaintext) {
        if (key == null || key.length == 0 || plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            String[] parts = MemoryCrypto.encrypt(key, plaintext);
            // parts = [ciphertext, nonce, tag]
            return parts[1] + parts[2] + parts[0]; // nonce + tag + ciphertext
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Encrypt error occurred: {}", LogEventType.MEMORY_PROCESS, e.getMessage());
            return "";
        }
    }

    /**
     * Decrypt ciphertext if a valid crypto key is provided.
     * Expected format: nonce_hex + tag_hex + ciphertext_hex
     * 
     * @param key key
     * @param ciphertext ciphertext
     * @return the result
     * @since 0.1.7
     */
    public static String decryptMemoryIfNeeded(byte[] key, String ciphertext) {
        if (key == null || key.length == 0 || ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        int nonceAndTagLen = NONCE_HEX_LENGTH + TAG_HEX_LENGTH;
        if (ciphertext.length() < nonceAndTagLen) {
            MEMORY_LOGGER.warn("[{}] Decryption error: invalid ciphertext length {}", LogEventType.MEMORY_PROCESS,
                    ciphertext.length());
            return "";
        }
        String nonce = ciphertext.substring(0, NONCE_HEX_LENGTH);
        String tag = ciphertext.substring(NONCE_HEX_LENGTH, nonceAndTagLen);
        String encryptedMemory = ciphertext.substring(nonceAndTagLen);
        try {
            return MemoryCrypto.decrypt(key, encryptedMemory, nonce, tag);
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Decrypt error occurred: {}", LogEventType.MEMORY_PROCESS, e.getMessage());
            return "";
        }
    }
}
