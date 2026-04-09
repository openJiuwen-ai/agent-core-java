  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates memory search across different memory type managers.
 */
public class SearchManager {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private static final Set<String> USER_MEM_MANAGER_LIST = Set.of(MemoryType.FRAGMENT_MEMORY.getValue());
    private static final Set<String> ALL_MEM_MANAGER_LIST = Arrays.stream(MemoryType.values())
            .map(MemoryType::getValue)
            .collect(Collectors.toSet());

    private final Map<String, BaseMemoryManager> managers;
    private final UserMemStore memStore;
    private final byte[] cryptoKey;

    public SearchManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore, byte[] cryptoKey) {
        this.managers = managers;
        this.memStore = memStore;
        this.cryptoKey = cryptoKey;
    }

    public List<Map<String, Object>> search(SearchParams params, SemanticStore semanticStore) {
        String userId = params.getUserId();
        String scopeId = params.getScopeId();
        String query = params.getQuery();
        int topK = params.getTopK();
        double threshold = params.getThreshold();
        String searchType = params.getSearchType();

        if (searchType != null && !ALL_MEM_MANAGER_LIST.contains(searchType)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", searchType,
                    "error_msg", searchType + " is not a valid search type");
        }
        if (searchType != null && !managers.containsKey(searchType)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", searchType,
                    "error_msg", searchType + " memory manager not inited");
        }

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("semantic_store", semanticStore);

        List<Map<String, Object>> result = new ArrayList<>();

        if (searchType == null) {
            for (Map.Entry<String, BaseMemoryManager> entry : managers.entrySet()) {
                if (USER_MEM_MANAGER_LIST.contains(entry.getKey())) {
                    List<Map<String, Object>> res = entry.getValue().search(
                            userId, scopeId, query, topK, kwargs);
                    if (res != null) {
                        result.addAll(res);
                    }
                }
            }
        } else {
            List<Map<String, Object>> res = managers.get(searchType).search(
                    userId, scopeId, query, topK, kwargs);
            if (res != null) {
                result = res;
            }
        }

        // Sort and truncate
        if (result.size() > topK) {
            result.sort((a, b) -> Double.compare(
                    ((Number) b.getOrDefault("score", 0.0)).doubleValue(),
                    ((Number) a.getOrDefault("score", 0.0)).doubleValue()));
        }

        return result.stream()
                .filter(item -> {
                    Object score = item.get("score");
                    double s = score instanceof Number ? ((Number) score).doubleValue() : 0.0;
                    return s >= threshold;
                })
                .limit(topK)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listUserMem(String userId, String scopeId,
                                                   int nums, int pages, String memType) {
        List<Map<String, Object>> listRes = memStore.getInRange(userId, scopeId,
                nums * (pages - 1), nums * pages, memType);
        if (listRes == null || listRes.isEmpty()) {
            return listRes;
        }
        for (Map<String, Object> item : listRes) {
            item.put("mem", BaseMemoryManager.decryptMemoryIfNeeded(cryptoKey,
                    String.valueOf(item.getOrDefault("mem", ""))));
        }
        return listRes;
    }

    public List<Map<String, Object>> listUserProfile(String userId, String scopeId, String profileType) {
        String key = MemoryType.FRAGMENT_MEMORY.getValue();
        if (!managers.containsKey(key)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " memory manager not inited");
        }
        BaseMemoryManager mgr = managers.get(key);
        if (!(mgr instanceof FragmentMemoryManager fragmentMgr)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " manager class is not FragmentMemoryManager");
        }
        return fragmentMgr.listFragmentMemories(userId, scopeId, profileType);
    }

    public List<Map<String, Object>> listUserProfile(String userId, String scopeId) {
        return listUserProfile(userId, scopeId, null);
    }

    public String getUserVariable(String userId, String scopeId, String varName) {
        String key = MemoryType.VARIABLE.getValue();
        if (!managers.containsKey(key)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " memory manager not inited");
        }
        BaseMemoryManager mgr = managers.get(key);
        if (!(mgr instanceof VariableManager varMgr)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " manager class is not VariableManager");
        }
        Map<String, String> res = varMgr.queryVariable(userId, scopeId, varName, null);
        if (res == null) return null;
        return res.get(varName);
    }

    public Map<String, String> getAllUserVariable(String userId, String scopeId) {
        String key = MemoryType.VARIABLE.getValue();
        if (!managers.containsKey(key)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " memory manager not inited");
        }
        BaseMemoryManager mgr = managers.get(key);
        if (!(mgr instanceof VariableManager varMgr)) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", key,
                    "error_msg", key + " manager class is not VariableManager");
        }
        return varMgr.queryVariable(userId, scopeId, null, null);
    }
}
