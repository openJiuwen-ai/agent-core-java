/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.UserProfileManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.openjiuwen.core.common.exception.BaseError;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchManager.
 * Converted from Python: test_search_manager.py
 */
class SearchManagerTest {

    private UserProfileManager mockUserProfileManager;
    private VariableManager mockVariableManager;
    private UserMemStore mockMemStore;
    private byte[] cryptoKey;
    private SearchManager searchManager;

    @BeforeEach
    void setUp() {
        mockUserProfileManager = mock(UserProfileManager.class);
        mockVariableManager = mock(VariableManager.class);
        mockMemStore = mock(UserMemStore.class);
        cryptoKey = new byte[0];

        // Setup default mock behavior
        when(mockUserProfileManager.search(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("id", "id1", "mem", "content1", "score", 0.9, "context_summary", ""),
                Map.of("id", "id2", "mem", "content2", "score", 0.8, "context_summary", "")
            )));
        when(mockUserProfileManager.listUserProfile(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        when(mockVariableManager.queryVariable(anyString(), anyString(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(Map.of("var1", "value1")));

        when(mockMemStore.getInRange(anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("id", "id1", "mem", "content1", "context_summary", ""),
                Map.of("id", "id2", "mem", "content2", "context_summary", "")
            )));

        Map<String, BaseMemoryManager> managers = Map.of(
            MemoryType.USER_PROFILE.getValue(), mockUserProfileManager,
            MemoryType.VARIABLE.getValue(), mockVariableManager
        );

        searchManager = new SearchManager(managers, mockMemStore, cryptoKey);
    }

    @Nested
    @DisplayName("Search Tests")
    class SearchTests {

        @Test
        @DisplayName("Test search with specific search_type")
        void testSearchWithSpecificType() {
            SearchParams params = SearchParams.builder()
                .userId("user1")
                .scopeId("scope1")
                .query("test query")
                .searchType(MemoryType.USER_PROFILE.getValue())
                .build();

            List<Map<String, Object>> result = searchManager.search(params).join();

            assertEquals(2, result.size());
            verify(mockUserProfileManager, times(1)).search(anyString(), anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Test search with invalid search_type raises error")
        void testSearchInvalidTypeRaisesError() {
            SearchParams params = SearchParams.builder()
                .userId("user1")
                .scopeId("scope1")
                .query("test")
                .searchType("invalid_type")
                .build();

            assertThrows(BaseError.class, () -> {
                searchManager.search(params).join();
            });
        }

        @Test
        @DisplayName("Test search with uninitialized manager raises error")
        void testSearchUninitializedManagerRaisesError() {
            // Create manager without user_profile manager
            SearchManager emptyManager = new SearchManager(Map.of(), mockMemStore, cryptoKey);

            SearchParams params = SearchParams.builder()
                .userId("user1")
                .scopeId("scope1")
                .query("test")
                .searchType(MemoryType.USER_PROFILE.getValue())
                .build();

            assertThrows(BaseError.class, () -> {
                emptyManager.search(params).join();
            });
        }

        @Test
        @DisplayName("Test search filters results below threshold")
        void testSearchFiltersByThreshold() {
            when(mockUserProfileManager.search(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                    Map.of("id", "id1", "mem", "content1", "score", 0.9, "context_summary", ""),
                    Map.of("id", "id2", "mem", "content2", "score", 0.2, "context_summary", "") // Below threshold
                )));

            SearchParams params = SearchParams.builder()
                .userId("user1")
                .scopeId("scope1")
                .query("test")
                .threshold(0.3)
                .build();

            List<Map<String, Object>> result = searchManager.search(params).join();

            assertEquals(1, result.size());
            assertEquals(0.9, (Double) result.get(0).get("score"), 0.01);
        }

        @Test
        @DisplayName("Test search limits results to top_k")
        void testSearchLimitsByTopK() {
            List<Map<String, Object>> manyResults = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                manyResults.add(Map.of(
                    "id", "id" + i,
                    "mem", "content" + i,
                    "score", 0.9 - i * 0.05,
                    "context_summary", ""
                ));
            }
            when(mockUserProfileManager.search(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(manyResults));

            SearchParams params = SearchParams.builder()
                .userId("user1")
                .scopeId("scope1")
                .query("test")
                .topK(3)
                .threshold(0.0)
                .build();

            List<Map<String, Object>> result = searchManager.search(params).join();

            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("List User Mem Tests")
    class ListUserMemTests {

        @Test
        @DisplayName("Test list_user_mem returns paginated results")
        void testListUserMemSuccess() {
            List<Map<String, Object>> result = searchManager.listUserMem("user1", "scope1", 10, 1).join();

            assertNotNull(result);
            verify(mockMemStore, times(1)).getInRange("user1", "scope1", 0, 10);
        }

        @Test
        @DisplayName("Test list_user_mem calculates correct pagination range")
        void testListUserMemPagination() {
            searchManager.listUserMem("user1", "scope1", 10, 3).join();

            // Page 3 with 10 items per page: start=20, end=30
            verify(mockMemStore, times(1)).getInRange("user1", "scope1", 20, 30);
        }
    }

    @Nested
    @DisplayName("List User Profile Tests")
    class ListUserProfileTests {

        @Test
        @DisplayName("Test list_user_profile calls manager")
        void testListUserProfileSuccess() {
            searchManager.listUserProfile("user1", "scope1", null).join();

            verify(mockUserProfileManager, times(1)).listUserProfile(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Test list_user_profile passes profile_type filter")
        void testListUserProfileWithTypeFilter() {
            searchManager.listUserProfile("user1", "scope1", "interests").join();

            verify(mockUserProfileManager, times(1)).listUserProfile("user1", "scope1", "interests");
        }

        @Test
        @DisplayName("Test list_user_profile raises when manager not initialized")
        void testListUserProfileMissingManagerRaises() {
            SearchManager emptyManager = new SearchManager(Map.of(), mockMemStore, cryptoKey);

            assertThrows(BaseError.class, () -> {
                emptyManager.listUserProfile("user1", "scope1", null).join();
            });
        }
    }

    @Nested
    @DisplayName("Get User Variable Tests")
    class GetUserVariableTests {

        @Test
        @DisplayName("Test get_user_variable returns variable value")
        void testGetUserVariableSuccess() {
            when(mockVariableManager.queryVariable(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("test_var", "test_value")));

            String result = searchManager.getUserVariable("user1", "scope1", "test_var").join();

            assertEquals("test_value", result);
        }

        @Test
        @DisplayName("Test get_user_variable returns null when variable not found")
        void testGetUserVariableNotFoundReturnsNone() {
            when(mockVariableManager.queryVariable(anyString(), anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            String result = searchManager.getUserVariable("user1", "scope1", "nonexistent").join();

            assertNull(result);
        }

        @Test
        @DisplayName("Test get_user_variable raises when manager not initialized")
        void testGetUserVariableMissingManagerRaises() {
            SearchManager emptyManager = new SearchManager(Map.of(), mockMemStore, cryptoKey);

            assertThrows(BaseError.class, () -> {
                emptyManager.getUserVariable("user1", "scope1", "test").join();
            });
        }
    }

    @Nested
    @DisplayName("Get All User Variable Tests")
    class GetAllUserVariableTests {

        @Test
        @DisplayName("Test get_all_user_variable returns all variables")
        void testGetAllUserVariableSuccess() {
            when(mockVariableManager.queryVariable(anyString(), anyString(), isNull(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                    "var1", "value1",
                    "var2", "value2"
                )));

            Map<String, Object> result = searchManager.getAllUserVariable("user1", "scope1").join();

            assertEquals(Map.of("var1", "value1", "var2", "value2"), result);
        }
    }
}



