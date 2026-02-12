/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.common.CryptoUtils;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified abstract base class for memory manager implementations.
 * Managing a specific type of memory data.
 * <p>
 * Corresponds to Python: manage/index/base_memory_manager.py
 */
public abstract class BaseMemoryManager {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    // hex_length = bytes_length * 2
    public static final int NONCE_HEX_LENGTH = CryptoUtils.NONCE_LENGTH * 2;
    public static final int TAG_HEX_LENGTH = CryptoUtils.TAG_LENGTH * 2;

    /**
     * Add memory.
     *
     * @param memory the memory unit to add
     * @param llmInfo optional LLM info as Pair of (name, Model)
     * @return CompletableFuture that completes when the operation is done
     */
    public abstract CompletableFuture<Void> add(BaseMemoryUnit memory, Pair<String, Model> llmInfo);

    /**
     * Update memory by its id.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @param memId the memory ID
     * @param newMemory the new memory content
     * @return CompletableFuture containing true if successful
     */
    public abstract CompletableFuture<Boolean> update(String userId, String scopeId, String memId, String newMemory);

    /**
     * Delete memory by its id.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @param memId the memory ID
     * @return CompletableFuture containing true if successful
     */
    public abstract CompletableFuture<Boolean> delete(String userId, String scopeId, String memId);

    /**
     * Delete memory by user id and scope id.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @return CompletableFuture containing true if successful
     */
    public abstract CompletableFuture<Boolean> deleteByUserId(String userId, String scopeId);

    /**
     * Get memory by its id.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @param memId the memory ID
     * @return CompletableFuture containing the memory data or null if not found
     */
    public abstract CompletableFuture<Map<String, Object>> get(String userId, String scopeId, String memId);

    /**
     * Query memory, return top k results.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @param query the query string
     * @param topK the number of results to return
     * @return CompletableFuture containing the search results
     */
    public abstract CompletableFuture<List<Map<String, Object>>> search(String userId, String scopeId, String query, int topK);

    /**
     * Encrypt memory content if key is provided and plaintext is not empty.
     *
     * @param key the encryption key
     * @param plaintext the plaintext to encrypt
     * @return encrypted string in format nonce+tag+ciphertext, or original plaintext if key is empty,
     *         or empty string if encryption fails
     */
    public static String encryptMemoryIfNeeded(byte[] key, String plaintext) {
        if (key == null || key.length == 0 || plaintext == null) {
            return plaintext;
        }

        if (plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            CryptoUtils.EncryptResult result = CryptoUtils.encrypt(key, plaintext);
            return result.nonce() + result.tag() + result.ciphertext();
        } catch (IllegalArgumentException e) {
            logger.warning("Encrypt exception occurred: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            logger.warning("Encrypt error occurred: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Decrypt memory content if key is provided and ciphertext is valid.
     *
     * @param key the decryption key
     * @param ciphertext the ciphertext in format nonce+tag+encrypted
     * @return decrypted plaintext, or original ciphertext if key is empty,
     *         or empty string if decryption fails
     */
    public static String decryptMemoryIfNeeded(byte[] key, String ciphertext) {
        if (key == null || key.length == 0 || ciphertext == null) {
            return ciphertext;
        }

        if (ciphertext.isEmpty()) {
            return ciphertext;
        }

        int nonceAndTagLen = NONCE_HEX_LENGTH + TAG_HEX_LENGTH;
        if (ciphertext.length() < nonceAndTagLen) {
            logger.warning("Decryption error occurred: invalid ciphertext len {}", ciphertext.length());
            return "";
        }

        String nonce = ciphertext.substring(0, NONCE_HEX_LENGTH);
        String tag = ciphertext.substring(NONCE_HEX_LENGTH, nonceAndTagLen);
        String encryptedMemory = ciphertext.substring(nonceAndTagLen);

        try {
            return CryptoUtils.decrypt(key, encryptedMemory, nonce, tag);
        } catch (IllegalArgumentException e) {
            logger.warning("Decrypt exception occurred: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            logger.warning("Decrypt error occurred: {}", e.getMessage());
            return "";
        }
    }
}

