/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.UserProfileManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manager for memory search operations.
 * <p>
 * Corresponds to Python: manage/search/search_manager.py
 */
public class SearchManager {

    private static final List<String> USER_MEM_MANAGER_LIST = List.of(MemoryType.USER_PROFILE.getValue());
    private static final List<String> ALL_MEM_MANAGER_LIST = Arrays.stream(MemoryType.values())
            .map(MemoryType::getValue)
            .collect(Collectors.toList());

    private final Map<String, BaseMemoryManager> managers;
    private final UserMemStore memStore;
    private final byte[] cryptoKey;

    /**
     * Initialize SearchManager.
     *
     * @param managers  Map of memory type to their corresponding managers
     * @param memStore  The user memory store
     * @param cryptoKey The encryption key for decrypting memory content
     */
    public SearchManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore, byte[] cryptoKey) {
        this.managers = managers;
        this.memStore = memStore;
        this.cryptoKey = cryptoKey != null ? cryptoKey : new byte[0];
    }

    /**
     * Search for memories matching the given parameters.
     *
     * @param params Search parameters
     * @return CompletableFuture containing list of matching memories
     */
    public CompletableFuture<List<Map<String, Object>>> search(SearchParams params) {
        String userId = params.getUserId();
        String scopeId = params.getScopeId();
        String query = params.getQuery();
        int topK = params.getTopK();
        double threshold = params.getThreshold();
        String searchType = params.getSearchType();

        // Validate search_type
        if (searchType != null && !ALL_MEM_MANAGER_LIST.contains(searchType)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    searchType + " is not a valid search type"
            );
        }

        // Check if manager is initialized for the search type
        if (searchType != null && !managers.containsKey(searchType)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    searchType + " memory manager not inited"
            );
        }

        CompletableFuture<List<Map<String, Object>>> resultFuture;

        if (searchType == null) {
            // Search across all available managers
            List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
            for (Map.Entry<String, BaseMemoryManager> entry : managers.entrySet()) {
                if (USER_MEM_MANAGER_LIST.contains(entry.getKey())) {
                    futures.add(entry.getValue().search(userId, scopeId, query, topK));
                }
            }

            resultFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<Map<String, Object>> combined = new ArrayList<>();
                        for (CompletableFuture<List<Map<String, Object>>> future : futures) {
                            List<Map<String, Object>> res = future.join();
                            if (res != null) {
                                combined.addAll(res);
                            }
                        }
                        return combined;
                    });
        } else {
            // Search using specific manager
            resultFuture = managers.get(searchType).search(userId, scopeId, query, topK)
                    .thenApply(res -> res != null ? res : new ArrayList<>());
        }

        return resultFuture.thenApply(result -> {
            // Sort by score descending
            if (result.size() > topK) {
                result.sort((a, b) -> {
                    Double scoreA = getScore(a);
                    Double scoreB = getScore(b);
                    return scoreB.compareTo(scoreA);
                });
            }
            // Filter by threshold and limit to topK
            return result.stream()
                    .filter(item -> getScore(item) >= threshold)
                    .limit(topK)
                    .collect(Collectors.toList());
        });
    }

    /**
     * List user memories with pagination.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param nums    Number of items per page
     * @param pages   Page number (1-based)
     * @return CompletableFuture containing list of user memories
     */
    public CompletableFuture<List<Map<String, Object>>> listUserMem(String userId, String scopeId, int nums, int pages) {
        int start = nums * (pages - 1);
        int end = nums * pages;
        return memStore.getInRange(userId, scopeId, start, end)
                .thenApply(listRes -> {
                    if (listRes == null || listRes.isEmpty()) {
                        return listRes != null ? new ArrayList<>(listRes) : new ArrayList<>();
                    }
                    // Create mutable copy to avoid modifying immutable list
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Map<String, Object> item : listRes) {
                        Map<String, Object> mutableItem = new HashMap<>(item);
                        mutableItem.put("mem", BaseMemoryManager.decryptMemoryIfNeeded(cryptoKey, (String) item.get("mem")));
                        mutableItem.put("context_summary", BaseMemoryManager.decryptMemoryIfNeeded(cryptoKey,
                                (String) item.get("context_summary")));
                        result.add(mutableItem);
                    }
                    return result;
                });
    }

    /**
     * List user profiles with optional filtering.
     *
     * @param userId      The user ID
     * @param scopeId     The scope ID
     * @param profileType Optional profile type filter
     * @return CompletableFuture containing list of user profiles
     */
    public CompletableFuture<List<Map<String, Object>>> listUserProfile(String userId, String scopeId, String profileType) {
        if (!managers.containsKey(MemoryType.USER_PROFILE.getValue())) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.USER_PROFILE.getValue() + " memory manager not inited"
            );
        }

        BaseMemoryManager manager = managers.get(MemoryType.USER_PROFILE.getValue());
        if (!(manager instanceof UserProfileManager userProfileManager)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.USER_PROFILE.getValue() + " manager class is not UserProfileManager"
            );
        }

        return userProfileManager.listUserProfile(userId, scopeId, profileType);
    }

    /**
     * Get a specific user variable.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @param varName The variable name
     * @return CompletableFuture containing the variable value or null
     */
    public CompletableFuture<String> getUserVariable(String userId, String scopeId, String varName) {
        if (!managers.containsKey(MemoryType.VARIABLE.getValue())) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.VARIABLE.getValue() + " memory manager not inited"
            );
        }

        BaseMemoryManager manager = managers.get(MemoryType.VARIABLE.getValue());
        if (!(manager instanceof VariableManager variableManager)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.VARIABLE.getValue() + " manager class is not VariableManager"
            );
        }

        return variableManager.queryVariable(userId, scopeId, varName, null)
                .thenApply(res -> {
                    if (res == null) {
                        return null;
                    }
                    Object value = res.get(varName);
                    return value != null ? value.toString() : null;
                });
    }

    /**
     * Get all user variables.
     *
     * @param userId  The user ID
     * @param scopeId The scope ID
     * @return CompletableFuture containing map of variable names to values
     */
    public CompletableFuture<Map<String, Object>> getAllUserVariable(String userId, String scopeId) {
        if (!managers.containsKey(MemoryType.VARIABLE.getValue())) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.VARIABLE.getValue() + " memory manager not inited"
            );
        }

        BaseMemoryManager manager = managers.get(MemoryType.VARIABLE.getValue());
        if (!(manager instanceof VariableManager variableManager)) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    MemoryType.VARIABLE.getValue() + " manager class is not VariableManager"
            );
        }

        return variableManager.queryVariable(userId, scopeId, null, null);
    }

    private static Double getScore(Map<String, Object> item) {
        Object score = item.get("score");
        if (score instanceof Double) {
            return (Double) score;
        }
        if (score instanceof Number) {
            return ((Number) score).doubleValue();
        }
        return 0.0;
    }
}

