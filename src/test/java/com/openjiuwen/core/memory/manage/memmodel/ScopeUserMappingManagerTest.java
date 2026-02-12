/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScopeUserMappingManager.
 * Corresponds to Python: test_scope_user_mapping_manager.py
 */
@DisplayName("ScopeUserMappingManager Tests")
class ScopeUserMappingManagerTest {

    private SqlDbStore mockSqlDbStore;
    private ScopeUserMappingManager manager;
    private List<Map<String, Object>> storedData;

    @BeforeEach
    void setUp() {
        storedData = new ArrayList<>();
        mockSqlDbStore = mock(SqlDbStore.class);

        // Mock write
        when(mockSqlDbStore.write(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> data = invocation.getArgument(1);
            storedData.add(new HashMap<>(data));
            return CompletableFuture.completedFuture(true);
        });

        // Mock exist
        when(mockSqlDbStore.exist(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> conditions = invocation.getArgument(1);
            for (Map<String, Object> item : storedData) {
                boolean match = true;
                for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                    if (!Objects.equals(item.get(entry.getKey()), entry.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return CompletableFuture.completedFuture(true);
                }
            }
            return CompletableFuture.completedFuture(false);
        });

        // Mock delete
        when(mockSqlDbStore.delete(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> conditions = invocation.getArgument(1);
            int originalLen = storedData.size();
            storedData.removeIf(item -> {
                for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                    if (!Objects.equals(item.get(entry.getKey()), entry.getValue())) {
                        return false;
                    }
                }
                return true;
            });
            return CompletableFuture.completedFuture(storedData.size() < originalLen);
        });

        // Mock conditionGet
        when(mockSqlDbStore.conditionGet(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            Map<String, List<Object>> conditions = invocation.getArgument(1);
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> item : storedData) {
                boolean match = true;
                for (Map.Entry<String, List<Object>> entry : conditions.entrySet()) {
                    if (!entry.getValue().contains(item.get(entry.getKey()))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    results.add(item);
                }
            }
            return CompletableFuture.completedFuture(results);
        });

        manager = new ScopeUserMappingManager(mockSqlDbStore);
    }

    @Nested
    @DisplayName("TestScopeUserMappingManagerAdd")
    class TestAdd {

        @Test
        @DisplayName("Test adding a new scope-user mapping")
        void testAddNewMapping() throws Exception {
            manager.add("user1", "scope1").get();

            assertEquals(1, storedData.size());
            assertEquals("user1", storedData.get(0).get("user_id"));
            assertEquals("scope1", storedData.get(0).get("scope_id"));
        }

        @Test
        @DisplayName("Test adding existing mapping doesn't create duplicate")
        void testAddExistingMappingDoesNothing() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user1", "scope1").get();

            assertEquals(1, storedData.size());
        }

        @Test
        @DisplayName("Test adding different users to same scope")
        void testAddDifferentUsersSameScope() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user2", "scope1").get();

            assertEquals(2, storedData.size());
        }

        @Test
        @DisplayName("Test adding same user to different scopes")
        void testAddSameUserDifferentScopes() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user1", "scope2").get();

            assertEquals(2, storedData.size());
        }

        @Test
        @DisplayName("Test adding with empty user_id")
        void testAddEmptyUserId() throws Exception {
            manager.add("", "scope1").get();

            assertEquals("", storedData.get(0).get("user_id"));
        }
    }

    @Nested
    @DisplayName("TestScopeUserMappingManagerDeleteByScopeId")
    class TestDeleteByScopeId {

        @Test
        @DisplayName("Test deleting mappings for a scope")
        void testDeleteExistingScope() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user2", "scope1").get();

            Boolean result = manager.deleteByScopeId("scope1").get();

            assertTrue(result);
            assertEquals(0, storedData.size());
        }

        @Test
        @DisplayName("Test deleting nonexistent scope returns False")
        void testDeleteNonexistentScope() throws Exception {
            Boolean result = manager.deleteByScopeId("nonexistent").get();

            assertFalse(result);
        }

        @Test
        @DisplayName("Test delete only removes mappings for target scope")
        void testDeleteOnlyAffectsTargetScope() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user2", "scope2").get();

            manager.deleteByScopeId("scope1").get();

            assertEquals(1, storedData.size());
            assertEquals("scope2", storedData.get(0).get("scope_id"));
        }
    }

    @Nested
    @DisplayName("TestScopeUserMappingManagerGetByScopeId")
    class TestGetByScopeId {

        @Test
        @DisplayName("Test get_by_scope_id returns all users in scope")
        void testGetReturnsAllUsersInScope() throws Exception {
            manager.add("user1", "scope1").get();
            manager.add("user2", "scope1").get();
            manager.add("user3", "scope2").get();

            List<Map<String, Object>> result = manager.getByScopeId("scope1").get();

            assertNotNull(result);
            assertEquals(2, result.size());
            List<String> userIds = result.stream()
                    .map(r -> (String) r.get("user_id"))
                    .toList();
            assertTrue(userIds.contains("user1"));
            assertTrue(userIds.contains("user2"));
        }

        @Test
        @DisplayName("Test get_by_scope_id returns null for nonexistent scope")
        void testGetNonexistentScopeReturnsNone() throws Exception {
            List<Map<String, Object>> result = manager.getByScopeId("nonexistent").get();

            assertNull(result);
        }

        @Test
        @DisplayName("Test get_by_scope_id returns null after all users deleted")
        void testGetEmptyScopeReturnsNone() throws Exception {
            manager.add("user1", "scope1").get();
            manager.deleteByScopeId("scope1").get();

            List<Map<String, Object>> result = manager.getByScopeId("scope1").get();

            assertNull(result);
        }
    }
}

