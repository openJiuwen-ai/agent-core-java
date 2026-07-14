/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Coordinates search and listing operations across memory managers.
 *
 * <p>Mirrors Python's {@code SearchManager} in
 * {@code openjiuwen/core/memory/manage/search/search_manager.py}.</p>
 */
public class SearchManager {
    private static final String MEMORY_TYPE_KEY = "memory_type";
    private static final String ERROR_MSG_KEY = "error_msg";
    private static final String MEM_TYPES_KEY = "mem_types";
    private static final String SCORE_KEY = "score";

    private static final Set<String> ALL_MEM_MANAGER_LIST = Arrays.stream(MemoryType.values())
            .map(MemoryType::getValue)
            .collect(Collectors.toUnmodifiableSet());

    private final Map<String, BaseMemoryManager> managers;
    private final byte[] cryptoKey;
    private final BaseMemoryIndex memoryIndex;

    public SearchManager(Map<String, BaseMemoryManager> managers, byte[] cryptoKey, BaseMemoryIndex memoryIndex) {
        this.managers = managers == null ? new LinkedHashMap<>() : managers;
        this.cryptoKey = cryptoKey == null ? null : cryptoKey.clone();
        this.memoryIndex = memoryIndex;
    }

    public CompletionStage<List<Map<String, Object>>> search(SearchParams params) {
        return search(params, Map.of());
    }

    public CompletionStage<List<Map<String, Object>>> search(SearchParams params, Map<String, Object> kwargs) {
        Objects.requireNonNull(params, "params");
        String userId = params.getUserId();
        String scopeId = params.getScopeId();
        String query = params.getQuery();
        int topK = params.getTopK();
        double threshold = params.getThreshold();
        List<String> searchType = params.getSearchType();

        if (topK <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        Map<String, Object> baseKwargs = copyKwargs(kwargs);
        baseKwargs.put(MEM_TYPES_KEY, searchType);

        if (searchType != null) {
            for (String type : searchType) {
                if (!ALL_MEM_MANAGER_LIST.contains(type)) {
                    throw memoryError(type, String.valueOf(type) + " is not a valid search type");
                }
            }
        }

        CompletionStage<List<Map<String, Object>>> aggregate =
                CompletableFuture.completedFuture(new ArrayList<>());
        if (searchType == null) {
            Set<BaseMemoryManager> uniqueManagers = new LinkedHashSet<>(managers.values());
            for (BaseMemoryManager manager : uniqueManagers) {
                Map<String, Object> callKwargs = copyKwargs(baseKwargs);
                aggregate = appendSearchResults(aggregate, manager, userId, scopeId, query, topK, callKwargs);
            }
        } else {
            Map<BaseMemoryManager, List<String>> usedTypes = new LinkedHashMap<>();
            for (String type : searchType) {
                if (type != null && !type.isEmpty() && (!managers.containsKey(type) || managers.get(type) == null)) {
                    throw memoryError(type, type + " memory manager not inited");
                }
                BaseMemoryManager manager = managers.get(type);
                usedTypes.computeIfAbsent(manager, ignored -> new ArrayList<>()).add(type);
            }
            for (Map.Entry<BaseMemoryManager, List<String>> entry : usedTypes.entrySet()) {
                Map<String, Object> callKwargs = copyKwargs(baseKwargs);
                callKwargs.put(MEM_TYPES_KEY, entry.getValue());
                aggregate = appendSearchResults(aggregate, entry.getKey(), userId, scopeId, query, topK, callKwargs);
            }
        }

        return aggregate.thenApply(result -> filterByScoreAndLimit(result, threshold, topK));
    }

    public CompletionStage<List<Map<String, Object>>> listUserMem(String userId,
                                                                  String scopeId,
                                                                  int nums,
                                                                  int pages) {
        return listUserMem(userId, scopeId, nums, pages, null);
    }

    public CompletionStage<List<Map<String, Object>>> listUserMem(String userId,
                                                                  String scopeId,
                                                                  int nums,
                                                                  int pages,
                                                                  String memType) {
        int start = nums * (pages - 1);
        if (memoryIndex == null) {
            throw memoryError("search_memory", "memory index not inited");
        }
        List<String> memTypes = memType == null || memType.isEmpty() ? List.of() : List.of(memType);
        return memoryIndex.listMemories(userId, scopeId, start, nums, memTypes)
                .thenApply(memoryDocs -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (MemoryDoc memoryDoc : memoryDocs) {
                        result.add(toUserMemoryMap(memoryDoc, userId, scopeId));
                    }
                    return result;
                });
    }

    public CompletionStage<List<Map<String, Object>>> listUserProfile(String userId, String scopeId) {
        for (String fragmentType : FragmentMemoryManager.FRAGMENT_MEMORY_TYPE) {
            if (!managers.containsKey(fragmentType)) {
                throw memoryError("fragment_memory", "fragment memory manager not inited");
            }
        }
        BaseMemoryManager manager = managers.get(MemoryType.USER_PROFILE.getValue());
        if (!(manager instanceof FragmentMemoryManager fragmentMemoryManager)) {
            throw memoryError("fragment_memory", "fragment memory manager class is not FragmentMemoryManager");
        }
        return fragmentMemoryManager.listFragmentMemories(userId, scopeId);
    }

    public CompletionStage<List<Map<String, Object>>> listUserSummary(String userId, String scopeId) {
        String memoryType = MemoryType.SUMMARY.getValue();
        if (!managers.containsKey(memoryType)) {
            throw memoryError(memoryType, memoryType + " memory manager not inited");
        }
        BaseMemoryManager manager = managers.get(memoryType);
        if (!(manager instanceof SummaryManager summaryManager)) {
            throw memoryError(memoryType, memoryType + " manager class is not SummaryManager");
        }
        return summaryManager.listUserSummary(userId, scopeId);
    }

    public CompletionStage<String> getUserVariable(String userId, String scopeId, String varName) {
        String memoryType = MemoryType.VARIABLE.getValue();
        if (!managers.containsKey(memoryType)) {
            throw memoryError(memoryType, memoryType + " memory manager not inited");
        }
        BaseMemoryManager manager = managers.get(memoryType);
        if (!(manager instanceof VariableManager variableManager)) {
            throw memoryError(memoryType, memoryType + " manager class is not VariableManager");
        }
        return variableManager.queryVariable(userId, scopeId, varName)
                .thenApply(result -> result == null || !result.containsKey(varName) ? null : result.get(varName));
    }

    public CompletionStage<Map<String, String>> getAllUserVariable(String userId, String scopeId) {
        String memoryType = MemoryType.VARIABLE.getValue();
        if (!managers.containsKey(memoryType)) {
            throw memoryError(memoryType, memoryType + " memory manager not inited");
        }
        BaseMemoryManager manager = managers.get(memoryType);
        if (!(manager instanceof VariableManager variableManager)) {
            throw memoryError(memoryType, memoryType + " manager class is not VariableManager");
        }
        return variableManager.queryVariable(userId, scopeId);
    }

    public byte[] getCryptoKey() {
        return cryptoKey == null ? null : cryptoKey.clone();
    }

    public BaseMemoryIndex getMemoryIndex() {
        return memoryIndex;
    }

    private CompletionStage<List<Map<String, Object>>> appendSearchResults(
            CompletionStage<List<Map<String, Object>>> aggregate,
            BaseMemoryManager manager,
            String userId,
            String scopeId,
            String query,
            int topK,
            Map<String, Object> kwargs) {
        return aggregate.thenCompose(result -> manager.search(userId, scopeId, query, topK, kwargs)
                .thenApply(searchResult -> {
                    if (searchResult != null) {
                        result.addAll(searchResult);
                    }
                    return result;
                }));
    }

    private static List<Map<String, Object>> filterByScoreAndLimit(List<Map<String, Object>> result,
                                                                   double threshold,
                                                                   int topK) {
        if (topK <= 0) {
            return List.of();
        }
        if (result.size() > topK) {
            result.sort(Comparator.comparingDouble(SearchManager::requiredScore).reversed());
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : result) {
            if (requiredScore(item) >= threshold) {
                filtered.add(item);
            }
        }
        int end = pythonSliceEnd(filtered.size(), topK);
        return new ArrayList<>(filtered.subList(0, end));
    }

    private static int pythonSliceEnd(int size, int topK) {
        if (topK >= 0) {
            return Math.min(size, topK);
        }
        return Math.max(size + topK, 0);
    }

    private static double requiredScore(Map<String, Object> item) {
        Object score = item.get(SCORE_KEY);
        if (!(score instanceof Number number)) {
            throw new ClassCastException("score is not numeric: " + score);
        }
        return number.doubleValue();
    }

    private static Map<String, Object> toUserMemoryMap(MemoryDoc memoryDoc, String userId, String scopeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", memoryDoc.getId());
        result.put("user_id", userId);
        result.put("scope_id", scopeId);
        result.put("mem", memoryDoc.getText());
        result.put("mem_type", memoryDoc.getType());
        result.put("timestamp", memoryDoc.getTimestamp());
        result.putAll(memoryDoc.getFields());
        return result;
    }

    private static Map<String, Object> copyKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
    }

    private static RuntimeException memoryError(String memoryType, String errorMsg) {
        return ErrorHelper.buildError(
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                MEMORY_TYPE_KEY,
                memoryType,
                ERROR_MSG_KEY,
                errorMsg
        );
    }
}
