
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.memory.support.TestInMemoryKVStore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class UserMemStoreTest {
    private static final String MEM_ID_1 = "000000000000000000000001";
    private static final String MEM_ID_2 = "000000000000000000000002";

    @Test
    void writeIndexesPythonFragmentMemoryTypesAndGlobalTopicIds() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        UserMemStore store = new UserMemStore(kvStore);

        assertTrue(store.write("user-1", "scope-1", MEM_ID_1, Map.of("mem_type", MemoryType.USER_PROFILE.getValue(),
                "profile_type", "ignored-by-python-layout", "mem", "profile")));
        assertTrue(store.write("user-1", "scope-1", MEM_ID_2,
                Map.of("mem_type", MemoryType.SEMANTIC_MEMORY.getValue(), "mem", "semantic")));

        assertEquals(MEM_ID_1 + MEM_ID_2, kvStore.get("UMD/user-1/scope-1/UPT/ids"));
        assertNull(kvStore.get("UMD/user-1/scope-1/UPT/ignored-by-python-layout/ids"));
        assertEquals(1, store.getAll("user-1", "scope-1", MemoryType.USER_PROFILE.getValue()).size());
        assertEquals(1, store.getAll("user-1", "scope-1", MemoryType.SEMANTIC_MEMORY.getValue()).size());
        assertNull(store.getByTopic("user-1", "scope-1", "ignored-by-python-layout"));
    }

    @Test
    void deleteRemovesFragmentIdsFromTypeTopicAndUserIndexes() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        UserMemStore store = new UserMemStore(kvStore);

        store.write("user-1", "scope-1", MEM_ID_1,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem_id", MEM_ID_1, "mem", "profile"));
        store.write("user-1", "scope-1", MEM_ID_2,
                Map.of("mem_type", MemoryType.EPISODIC_MEMORY.getValue(), "mem_id", MEM_ID_2, "mem", "episodic"));

        store.delete("user-1", "scope-1", MEM_ID_1);

        assertEquals(MEM_ID_2, kvStore.get("UMD/user-1/scope-1/UPT/ids"));
        assertNull(kvStore.get("UMD/user-1/scope-1/user_profile/ids"));
        assertEquals(MEM_ID_2, kvStore.get("UMD/user-1/scope-1/episodic_memory/ids"));
        assertEquals(List.of(MEM_ID_2), store.getInRange("user-1", "scope-1", 0, 10, null).stream()
                .map(item -> String.valueOf(item.get("mem_id"))).toList());
    }

    @Test
    void writeRejectsEmptyDataAndDuplicateMemoryId() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        UserMemStore store = new UserMemStore(kvStore);

        assertFalse(store.write("user-1", "scope-1", MEM_ID_1, Map.of()));
        assertTrue(store.write("user-1", "scope-1", MEM_ID_1,
                Map.of("mem_type", MemoryType.SUMMARY.getValue(), "mem_id", MEM_ID_1, "summary", "summary")));
        assertFalse(store.write("user-1", "scope-1", MEM_ID_1,
                Map.of("mem_type", MemoryType.SUMMARY.getValue(), "mem_id", MEM_ID_1, "summary", "duplicate")));
    }
}
