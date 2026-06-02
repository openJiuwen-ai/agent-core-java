/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.manage;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for Memory Manage module.
 *
 * <p>Mirrors Python's {@code test_manage.py} from
 * {@code tests/unit_tests/core/memory/manage/test_manage.py}.
 */
class TestManage {

    @Test
    void testBasic() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        DataIdManager dataIdGenerator = new DataIdManager();
        UserMemStore memStore = new UserMemStore(kvStore);
        SemanticStore semanticStore = mockSemanticStore();

        FragmentMemoryManager userProfileManager = new FragmentMemoryManager(memStore, dataIdGenerator, new byte[0]);
        VariableManager variableManager = new VariableManager(kvStore, new byte[0]);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.FRAGMENT_MEMORY.getValue(), userProfileManager);
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        WriteManager writeManager = new WriteManager(managers, memStore);

        addTestMemories(writeManager, semanticStore, "usrZH2025", "fitnesstrackerv3", List.of(
                "用户非常喜欢川菜，尤其是水煮鱼和麻婆豆腐",
                "用户的职业是软件工程师，居住在北京市",
                "用户的副业是短视频直播",
                "用户的银行卡账户余额为10000元",
                "用户的朋友圈中有50个好友",
                "用户的宠物是一只金毛犬"
        ));
        addTestMemories(writeManager, semanticStore, "usrZH2026", "fitnesstrackerv3", List.of(
                "用户喜欢打篮球和阅读历史小说",
                "用户的生日是1990年1月1日",
                "用户的汽车型号是特斯拉Model 3",
                "用户在Twitter上有200个关注者"
        ));

        List<Map<String, Object>> searchResults = userProfileManager.search(
                "usrZH2025",
                "fitnesstrackerv3",
                "用户的职业",
                5,
                Map.of("semantic_store", semanticStore)
        );
        assertEquals(5, searchResults.size());

        Map<String, Object> firstResult = searchResults.getFirst();
        userProfileManager.update(
                String.valueOf(firstResult.get("user_id")),
                String.valueOf(firstResult.get("scope_id")),
                String.valueOf(firstResult.get("id")),
                "用户不是软件工程师，而是系统分析师",
                Map.of("semantic_store", semanticStore)
        );
        Map<String, Object> updated = userProfileManager.get(
                String.valueOf(firstResult.get("user_id")),
                String.valueOf(firstResult.get("scope_id")),
                String.valueOf(firstResult.get("id"))
        );
        assertEquals("用户不是软件工程师，而是系统分析师", updated.get("mem"));

        List<Map<String, Object>> allMemories = userProfileManager.listFragmentMemories(
                "usrZH2025",
                "fitnesstrackerv3",
                null
        );
        assertEquals(6, allMemories.size());

        for (Map<String, Object> memory : allMemories.subList(0, 2)) {
            writeManager.deleteMemById(
                    String.valueOf(memory.get("user_id")),
                    String.valueOf(memory.get("scope_id")),
                    String.valueOf(memory.get("id")),
                    semanticStore
            );
        }

        List<Map<String, Object>> afterDelete = userProfileManager.search(
                "usrZH2025",
                "fitnesstrackerv3",
                "用户的职业",
                5,
                Map.of("semantic_store", semanticStore)
        );
        assertEquals(4, afterDelete.size());

        writeManager.deleteMemByUserId("usrZH2026", "fitnesstrackerv3", semanticStore);
        List<Map<String, Object>> otherUserResults = userProfileManager.search(
                "usrZH2026",
                "fitnesstrackerv3",
                "用户的职业",
                5,
                Map.of("semantic_store", semanticStore)
        );
        assertTrue(otherUserResults.isEmpty());
    }

    private static void addTestMemories(WriteManager writeManager,
                                        SemanticStore semanticStore,
                                        String userId,
                                        String scopeId,
                                        List<String> contents) {
        for (int index = 0; index < contents.size(); index++) {
            FragmentMemoryUnit fragment = FragmentMemoryUnit.builder()
                    .fragmentType("user_profile")
                    .content(contents.get(index))
                    .messageMemId("fragment-" + index)
                    .build();
            writeManager.addMemories(
                    userId,
                    scopeId,
                    Map.of(MemoryType.FRAGMENT_MEMORY.getValue(), List.of(fragment)),
                    null,
                    semanticStore
            );

            VariableUnit variable = VariableUnit.builder()
                    .variableName("memory_" + index)
                    .variableMem(contents.get(index))
                    .build();
            writeManager.addMemories(
                    userId,
                    scopeId,
                    Map.of(MemoryType.VARIABLE.getValue(), List.of(variable)),
                    null,
                    semanticStore
            );
        }
    }

    private static SemanticStore mockSemanticStore() {
        SemanticStore semanticStore = mock(SemanticStore.class);
        Map<String, LinkedHashMap<String, String>> tables = new LinkedHashMap<>();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Map.Entry<String, String>> docs = invocation.getArgument(0);
            String tableName = invocation.getArgument(1);
            LinkedHashMap<String, String> table = tables.computeIfAbsent(tableName, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, String> doc : docs) {
                table.put(doc.getKey(), doc.getValue());
            }
            return true;
        }).when(semanticStore).addDocs(anyList(), anyString());

        doAnswer(invocation -> {
            String tableName = invocation.getArgument(1);
            int topK = invocation.getArgument(2);
            LinkedHashMap<String, String> table = tables.getOrDefault(tableName, new LinkedHashMap<>());
            List<Map.Entry<String, Double>> results = new ArrayList<>();
            int count = 0;
            for (String id : table.keySet()) {
                if (count++ >= topK) {
                    break;
                }
                results.add(new AbstractMap.SimpleEntry<>(id, 0.99));
            }
            return results;
        }).when(semanticStore).search(anyString(), anyString(), anyInt());

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> ids = invocation.getArgument(0);
            String tableName = invocation.getArgument(1);
            LinkedHashMap<String, String> table = tables.get(tableName);
            if (table != null) {
                ids.forEach(table::remove);
            }
            return null;
        }).when(semanticStore).deleteDocs(anyList(), anyString());

        doAnswer(invocation -> {
            String tableName = invocation.getArgument(0);
            tables.remove(tableName);
            return null;
        }).when(semanticStore).deleteTable(anyString());

        return semanticStore;
    }
}
