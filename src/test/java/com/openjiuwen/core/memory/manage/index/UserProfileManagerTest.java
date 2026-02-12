/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.memmodel.DataIdManager;
import com.openjiuwen.core.memory.manage.memmodel.SemanticStore;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;
import com.openjiuwen.core.memory.manage.memmodel.UserProfileUnit;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;
import com.openjiuwen.core.common.utils.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserProfileManager.
 * Converted from Python: test_user_profile_manager.py
 */
class UserProfileManagerTest {

    private InMemoryKVStore kvStore;
    private UserMemStore userMemStore;
    private DataIdManager dataIdManager;
    private SemanticStore semanticStore;
    private byte[] cryptoKey;
    private UserProfileManager userProfileManager;

    @BeforeEach
    void setUp() {
        kvStore = new InMemoryKVStore();
        userMemStore = new UserMemStore(kvStore);
        dataIdManager = new DataIdManager();
        semanticStore = mock(SemanticStore.class);
        cryptoKey = new byte[0]; // No encryption for simpler testing
        
        // Setup mock semantic store
        when(semanticStore.addDocs(anyList(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(semanticStore.deleteDocs(anyList(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(semanticStore.search(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(semanticStore.deleteTable(anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        
        userProfileManager = new UserProfileManager(
            semanticStore,
            userMemStore,
            dataIdManager,
            cryptoKey
        );
    }

    @Nested
    @DisplayName("Add Validation Tests")
    class AddValidationTests {

        @Test
        @DisplayName("Test add with non-UserProfileUnit raises error")
        void testAddNonUserProfileUnitRaisesError() {
            VariableUnit variableUnit = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("test")
                .variableMem("value")
                .build();

            assertThrows(BaseError.class, () -> {
                userProfileManager.add(variableUnit, null).join();
            });
        }

        @Test
        @DisplayName("Test add with empty user_id raises error")
        void testAddEmptyUserIdRaisesError() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            assertThrows(BaseError.class, () -> {
                userProfileManager.add(unit, null).join();
            });
        }

        @Test
        @DisplayName("Test add with empty scope_id raises error")
        void testAddEmptyScopeIdRaisesError() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            assertThrows(BaseError.class, () -> {
                userProfileManager.add(unit, null).join();
            });
        }

        @Test
        @DisplayName("Test add with empty profile_mem raises error")
        void testAddEmptyProfileMemRaisesError() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("")
                .build();

            assertThrows(BaseError.class, () -> {
                userProfileManager.add(unit, null).join();
            });
        }

        @Test
        @DisplayName("Test add with empty profile_type raises error")
        void testAddEmptyProfileTypeRaisesError() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("")
                .profileMem("喜欢川菜")
                .build();

            assertThrows(BaseError.class, () -> {
                userProfileManager.add(unit, null).join();
            });
        }
    }

    @Nested
    @DisplayName("Add With Conflict Tests")
    class AddWithConflictTests {

        @Test
        @DisplayName("Test adding a new profile stores to both KV and vector store")
        void testAddNewProfileStoresToKvAndVector() {
            // Without LLM, ConflictResolution returns ADD for new messages
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            userProfileManager.add(unit, null).join();

            // Verify semantic store was called
            verify(semanticStore, atLeastOnce()).addDocs(anyList(), anyString(), anyString());

            // Verify data was stored in KV
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            assertNotNull(stored);
            assertEquals(1, stored.size());
        }

        @Test
        @DisplayName("Test adding profile without LLM always adds (no conflict detection)")
        void testAddWithoutLlmAlwaysAdds() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            userProfileManager.add(unit, null).join();

            // Without LLM, should always add new memory
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            assertNotNull(stored);
            assertFalse(stored.isEmpty());
        }
    }

    @Nested
    @DisplayName("Update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Test update modifies both KV store and vector store")
        void testUpdateModifiesKvAndVector() {
            // First add a profile
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("原始内容")
                .build();
            userProfileManager.add(unit, null).join();

            // Get the stored profile
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            String memId = (String) stored.get(0).get("id");

            // Update it
            Boolean result = userProfileManager.update("user1", "scope1", memId, "更新后的内容").join();

            assertTrue(result);

            // Verify vector store operations
            verify(semanticStore, atLeastOnce()).deleteDocs(anyList(), anyString());
            verify(semanticStore, atLeast(2)).addDocs(anyList(), anyString(), anyString()); // Initial add + update
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Test delete removes from both KV and vector store")
        void testDeleteRemovesFromKvAndVector() {
            // First add a profile
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("要删除的内容")
                .build();
            userProfileManager.add(unit, null).join();

            // Get the stored profile
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            String memId = (String) stored.get(0).get("id");

            // Delete it
            Boolean result = userProfileManager.delete("user1", "scope1", memId).join();

            assertTrue(result);

            // Verify it's deleted
            List<Map<String, Object>> remaining = userMemStore.getAll("user1", "scope1", null).join();
            assertTrue(remaining == null || remaining.isEmpty());
        }

        @Test
        @DisplayName("Test deleting nonexistent profile returns False")
        void testDeleteNonexistentReturnsFalse() {
            Boolean result = userProfileManager.delete("user1", "scope1", "nonexistent").join();
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Delete By User ID Tests")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("Test delete_by_user_id removes all profiles for a user")
        void testDeleteAllUserProfiles() {
            // Add multiple profiles
            for (int i = 0; i < 3; i++) {
                UserProfileUnit unit = UserProfileUnit.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .profileType("type" + i)
                    .profileMem("内容" + i)
                    .build();
                userProfileManager.add(unit, null).join();
            }

            // Delete all
            Boolean result = userProfileManager.deleteByUserId("user1", "scope1").join();

            assertTrue(result);

            // Verify all deleted
            List<Map<String, Object>> remaining = userMemStore.getAll("user1", "scope1", null).join();
            assertTrue(remaining == null || remaining.isEmpty());
        }
    }

    @Nested
    @DisplayName("Search Tests")
    class SearchTests {

        @Test
        @DisplayName("Test search returns results sorted by score descending")
        void testSearchReturnsSortedByScore() {
            // Add profiles
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();
            userProfileManager.add(unit, null).join();

            // Get the stored mem_id
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            String memId = (String) stored.get(0).get("id");

            // Mock semantic search to return our mem_id
            when(semanticStore.search(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                    new Pair<>(memId, 0.9)
                )));

            List<Map<String, Object>> result = userProfileManager.search("user1", "scope1", "川菜", 5).join();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(0.9, (Double) result.get(0).get("score"), 0.01);
        }
    }

    @Nested
    @DisplayName("List User Profile Tests")
    class ListUserProfileTests {

        @Test
        @DisplayName("Test list_user_profile filters by profile_type")
        void testListFiltersByProfileType() {
            // Add profiles with different types
            String[] types = {"interests", "personal_info", "interests"};
            for (int i = 0; i < types.length; i++) {
                UserProfileUnit unit = UserProfileUnit.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .profileType(types[i])
                    .profileMem("内容_" + types[i] + "_" + i)
                    .build();
                userProfileManager.add(unit, null).join();
            }

            // List only interests
            List<Map<String, Object>> result = userProfileManager.listUserProfile("user1", "scope1", "interests").join();

            assertEquals(2, result.size());
            for (Map<String, Object> item : result) {
                assertEquals("interests", item.get("profile_type"));
            }
        }

        @Test
        @DisplayName("Test list_user_profile returns empty list when no profiles exist")
        void testListReturnsEmptyForNoProfiles() {
            List<Map<String, Object>> result = userProfileManager.listUserProfile("nonexistent", "scope1", null).join();
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Get Tests")
    class GetTests {

        @Test
        @DisplayName("Test get returns decrypted content when crypto_key is set")
        void testGetDecryptsContent() {
            byte[] encryptionKey = "1234567890abcdef1234567890abcdef".getBytes();

            UserProfileManager manager = new UserProfileManager(
                semanticStore,
                userMemStore,
                dataIdManager,
                encryptionKey
            );

            // Add a profile (will be encrypted)
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("敏感内容")
                .build();
            manager.add(unit, null).join();

            // Get the stored mem_id
            List<Map<String, Object>> stored = userMemStore.getAll("user1", "scope1", null).join();
            String memId = (String) stored.get(0).get("id");

            // Get and verify decryption
            Map<String, Object> result = manager.get("user1", "scope1", memId).join();

            assertEquals("敏感内容", result.get("mem"));
        }
    }
}
