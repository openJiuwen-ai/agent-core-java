/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.foundation.store.InMemoryKVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserMemStore.
 * Corresponds to Python: test_user_mem_store.py
 */
class UserMemStoreTest {

    private UserMemStore store;
    private DataIdManager dataIdManager;

    @BeforeEach
    void setUp() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        store = new UserMemStore(kvStore);
        dataIdManager = new DataIdManager();
    }

    private String generateNextId(String userId) {
        return dataIdManager.generateNextId(userId);
    }

    @Nested
    @DisplayName("Tests for write method")
    class TestWrite {

        @Test
        @DisplayName("Should write memory successfully")
        void testWriteSuccess() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            String memId = generateNextId(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", memId);
            data.put("user_id", userId);
            data.put("scope_id", scopeId);
            data.put("profile_type", "personal_information");
            data.put("profile_mem", "user profile1");
            data.put("mem_type", "user_profile");
            data.put("time", Instant.now().toString());

            boolean result = store.write(userId, scopeId, memId, data).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false for empty data")
        void testWriteEmptyDataReturnsFalse() throws ExecutionException, InterruptedException {
            boolean result = store.write("user1", "scope1", "mem1", new HashMap<>()).get();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests for get method")
    class TestGet {

        @Test
        @DisplayName("Should return memory data when exists")
        void testGetExistingMemory() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            String memId = generateNextId(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", memId);
            data.put("user_id", userId);
            data.put("scope_id", scopeId);
            data.put("profile_mem", "test content");
            data.put("mem_type", "user_profile");
            data.put("time", Instant.now().toString());

            store.write(userId, scopeId, memId, data).get();

            Map<String, Object> result = store.get(userId, scopeId, memId).get();

            assertEquals(data, result);
        }

        @Test
        @DisplayName("Should return null for non-existent memory")
        void testGetNonExistentMemoryReturnsNull() throws ExecutionException, InterruptedException {
            Map<String, Object> result = store.get("user1", "scope1", "nonexistent").get();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Tests for update method")
    class TestUpdate {

        @Test
        @DisplayName("Should update memory successfully")
        void testUpdateSuccess() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            String memId = generateNextId(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", memId);
            data.put("user_id", userId);
            data.put("scope_id", scopeId);
            data.put("profile_mem", "original content");
            data.put("mem_type", "user_profile");

            store.write(userId, scopeId, memId, data).get();

            String newContent = "updated content";
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("profile_mem", newContent);
            boolean result = store.update(userId, scopeId, memId, updateData).get();

            assertTrue(result);
            Map<String, Object> updatedData = store.get(userId, scopeId, memId).get();
            assertEquals(newContent, updatedData.get("profile_mem"));
        }

        @Test
        @DisplayName("Should return false for non-existent memory")
        void testUpdateNonExistentReturnsFalse() throws ExecutionException, InterruptedException {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("profile_mem", "new");
            boolean result = store.update("user1", "scope1", "nonexistent", updateData).get();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests for get_all method")
    class TestGetAll {

        @Test
        @DisplayName("Should return all memories for user")
        void testGetAllReturnsAllMemories() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";

            // Write multiple memories
            for (int i = 0; i < 3; i++) {
                String memId = generateNextId(userId);
                Map<String, Object> data = new HashMap<>();
                data.put("id", memId);
                data.put("user_id", userId);
                data.put("scope_id", scopeId);
                data.put("profile_mem", "content " + i);
                data.put("mem_type", "user_profile");
                store.write(userId, scopeId, memId, data).get();
            }

            List<Map<String, Object>> result = store.getAll(userId, scopeId, null).get();

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should filter by memory type")
        void testGetAllWithMemTypeFilter() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";

            // Write user_profile type
            String memId1 = generateNextId(userId);
            Map<String, Object> data1 = new HashMap<>();
            data1.put("id", memId1);
            data1.put("user_id", userId);
            data1.put("scope_id", scopeId);
            data1.put("mem_type", "user_profile");
            store.write(userId, scopeId, memId1, data1).get();

            // Write episodic_mem type
            String memId2 = generateNextId(userId);
            Map<String, Object> data2 = new HashMap<>();
            data2.put("id", memId2);
            data2.put("user_id", userId);
            data2.put("scope_id", scopeId);
            data2.put("mem_type", "episodic_mem");
            store.write(userId, scopeId, memId2, data2).get();

            List<Map<String, Object>> userProfiles = store.getAll(userId, scopeId, "user_profile").get();
            List<Map<String, Object>> episodicMems = store.getAll(userId, scopeId, "episodic_mem").get();

            assertEquals(1, userProfiles.size());
            assertEquals(1, episodicMems.size());
        }
    }

    @Nested
    @DisplayName("Tests for batch_get method")
    class TestBatchGet {

        @Test
        @DisplayName("Should return multiple memories")
        void testBatchGet() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            List<String> memIds = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                String memId = generateNextId(userId);
                memIds.add(memId);
                Map<String, Object> data = new HashMap<>();
                data.put("id", memId);
                data.put("user_id", userId);
                data.put("scope_id", scopeId);
                data.put("content", "content " + i);
                store.write(userId, scopeId, memId, data).get();
            }

            List<Map<String, Object>> result = store.batchGet(userId, scopeId, memIds).get();

            assertEquals(3, result.size());
            assertTrue(result.stream().allMatch(r -> r != null));
        }
    }

    @Nested
    @DisplayName("Tests for get_by_topic method")
    class TestGetByTopic {

        @Test
        @DisplayName("Should return memories by profile type")
        void testGetByTopic() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            String profileType = "personal_information";

            String memId = generateNextId(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", memId);
            data.put("user_id", userId);
            data.put("scope_id", scopeId);
            data.put("profile_type", profileType);
            data.put("mem_type", "user_profile");
            store.write(userId, scopeId, memId, data).get();

            List<Map<String, Object>> result = store.getByTopic(userId, scopeId, profileType).get();

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Tests for get_in_range method")
    class TestGetInRange {

        @Test
        @DisplayName("Should return memories in range")
        void testGetInRange() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";

            for (int i = 0; i < 5; i++) {
                String memId = generateNextId(userId);
                Map<String, Object> data = new HashMap<>();
                data.put("id", memId);
                data.put("user_id", userId);
                data.put("scope_id", scopeId);
                data.put("content", "content " + i);
                store.write(userId, scopeId, memId, data).get();
            }

            List<Map<String, Object>> result = store.getInRange(userId, scopeId, 0, 3).get();

            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("Tests for delete method")
    class TestDelete {

        @Test
        @DisplayName("Should delete memory")
        void testDelete() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            String memId = generateNextId(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", memId);
            data.put("user_id", userId);
            data.put("scope_id", scopeId);
            data.put("content", "test");
            store.write(userId, scopeId, memId, data).get();

            store.delete(userId, scopeId, memId).get();
            Map<String, Object> result = store.get(userId, scopeId, memId).get();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Tests for batch_delete method")
    class TestBatchDelete {

        @Test
        @DisplayName("Should delete multiple memories")
        void testBatchDelete() throws ExecutionException, InterruptedException {
            String userId = "user1";
            String scopeId = "scope1";
            List<String> memIds = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                String memId = generateNextId(userId);
                memIds.add(memId);
                Map<String, Object> data = new HashMap<>();
                data.put("id", memId);
                data.put("user_id", userId);
                data.put("scope_id", scopeId);
                data.put("content", "content " + i);
                store.write(userId, scopeId, memId, data).get();
            }

            store.batchDelete(userId, scopeId, memIds).get();
            List<Map<String, Object>> result = store.getAll(userId, scopeId, null).get();

            assertNull(result);
        }
    }
}

