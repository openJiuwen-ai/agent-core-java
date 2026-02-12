/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;
import com.openjiuwen.core.memory.manage.memmodel.UserProfileUnit;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WriteManager.
 * Converted from Python: test_write_manager.py
 */
class WriteManagerTest {

    private BaseMemoryManager mockUserProfileManager;
    private BaseMemoryManager mockVariableManager;
    private UserMemStore mockMemStore;
    private WriteManager writeManager;

    @BeforeEach
    void setUp() {
        mockUserProfileManager = mock(BaseMemoryManager.class);
        mockVariableManager = mock(BaseMemoryManager.class);
        mockMemStore = mock(UserMemStore.class);

        // Setup default mock behavior
        when(mockUserProfileManager.add(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockUserProfileManager.update(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(mockUserProfileManager.delete(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(mockUserProfileManager.deleteByUserId(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));

        when(mockVariableManager.add(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(mockVariableManager.update(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(mockVariableManager.delete(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));
        when(mockVariableManager.deleteByUserId(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(true));

        when(mockMemStore.get(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        Map<String, BaseMemoryManager> managers = Map.of(
            MemoryType.USER_PROFILE.getValue(), mockUserProfileManager,
            MemoryType.VARIABLE.getValue(), mockVariableManager
        );

        writeManager = new WriteManager(managers, mockMemStore);
    }

    @Nested
    @DisplayName("Add Mem Tests")
    class AddMemTests {

        @Test
        @DisplayName("Test user profile memory is dispatched to user_profile manager")
        void testAddUserProfileDispatchesCorrectly() {
            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            writeManager.addMem(List.of(unit), null).join();

            verify(mockUserProfileManager, times(1)).add(eq(unit), isNull());
        }

        @Test
        @DisplayName("Test variable memory is dispatched to variable manager")
        void testAddVariableDispatchesCorrectly() {
            VariableUnit unit = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("age")
                .variableMem("25")
                .build();

            writeManager.addMem(List.of(unit), null).join();

            verify(mockVariableManager, times(1)).add(eq(unit), isNull());
        }

        @Test
        @DisplayName("Test multiple memory units are dispatched to respective managers")
        void testAddMultipleUnits() {
            UserProfileUnit profileUnit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            VariableUnit variableUnit = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("age")
                .variableMem("25")
                .build();

            writeManager.addMem(List.of(profileUnit, variableUnit), null).join();

            verify(mockUserProfileManager, times(1)).add(any(), any());
            verify(mockVariableManager, times(1)).add(any(), any());
        }

        @Test
        @DisplayName("Test unsupported memory type logs warning")
        void testAddUnsupportedTypeLogsWarning() {
            // Only register user_profile manager
            Map<String, BaseMemoryManager> managers = Map.of(
                MemoryType.USER_PROFILE.getValue(), mockUserProfileManager
            );
            WriteManager managerWithLimitedTypes = new WriteManager(managers, mockMemStore);

            // Try to add a variable (not registered)
            VariableUnit unit = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("test")
                .variableMem("value")
                .build();

            // Should not raise, just log warning
            assertDoesNotThrow(() -> managerWithLimitedTypes.addMem(List.of(unit), null).join());
        }

        @Test
        @DisplayName("Test manager exception is caught and raises MEMORY_ADD_MEMORY_EXECUTION_ERROR")
        void testAddManagerExceptionRaisesError() {
            when(mockUserProfileManager.add(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("test error")));

            UserProfileUnit unit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            assertThrows(CompletionException.class, () -> {
                writeManager.addMem(List.of(unit), null).join();
            });
        }

        @Test
        @DisplayName("Test partial failure (one succeeds, one fails) still raises error")
        void testAddPartialFailureStillRaises() {
            when(mockUserProfileManager.add(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("profile error")));
            when(mockVariableManager.add(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null)); // success

            UserProfileUnit profileUnit = UserProfileUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .profileType("interests")
                .profileMem("喜欢川菜")
                .build();

            VariableUnit variableUnit = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("age")
                .variableMem("25")
                .build();

            assertThrows(CompletionException.class, () -> {
                writeManager.addMem(List.of(profileUnit, variableUnit), null).join();
            });

            // Both should have been attempted
            verify(mockUserProfileManager, times(1)).add(any(), any());
            verify(mockVariableManager, times(1)).add(any(), any());
        }
    }

    @Nested
    @DisplayName("Update Mem By ID Tests")
    class UpdateMemByIdTests {

        @Test
        @DisplayName("Test update dispatches to correct manager based on mem_type")
        void testUpdateDispatchesToCorrectManager() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                    "id", "mem123",
                    "mem_type", MemoryType.USER_PROFILE.getValue()
                )));

            writeManager.updateMemById("user1", "scope1", "mem123", "updated content").join();

            verify(mockUserProfileManager, times(1)).update("user1", "scope1", "mem123", "updated content");
        }

        @Test
        @DisplayName("Test update with nonexistent memory logs warning and returns")
        void testUpdateNonexistentMemoryLogsWarning() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            writeManager.updateMemById("user1", "scope1", "nonexistent", "content").join();

            verify(mockUserProfileManager, never()).update(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Test update with missing mem_type field logs warning")
        void testUpdateMissingMemTypeLogsWarning() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("id", "mem123"))); // no mem_type

            writeManager.updateMemById("user1", "scope1", "mem123", "content").join();

            verify(mockUserProfileManager, never()).update(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Test update with unsupported mem_type logs warning")
        void testUpdateUnsupportedMemTypeLogsWarning() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                    "id", "mem123",
                    "mem_type", "unknown_type"
                )));

            writeManager.updateMemById("user1", "scope1", "mem123", "content").join();

            verify(mockUserProfileManager, never()).update(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Delete Mem By ID Tests")
    class DeleteMemByIdTests {

        @Test
        @DisplayName("Test delete dispatches to correct manager based on mem_type")
        void testDeleteDispatchesToCorrectManager() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Map.of(
                    "id", "mem123",
                    "mem_type", MemoryType.USER_PROFILE.getValue()
                )));

            writeManager.deleteMemById("user1", "scope1", "mem123").join();

            verify(mockUserProfileManager, times(1)).delete("user1", "scope1", "mem123");
        }

        @Test
        @DisplayName("Test delete with nonexistent memory logs warning and returns")
        void testDeleteNonexistentMemoryLogsWarning() {
            when(mockMemStore.get(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            writeManager.deleteMemById("user1", "scope1", "nonexistent").join();

            verify(mockUserProfileManager, never()).delete(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Delete Mem By User ID Tests")
    class DeleteMemByUserIdTests {

        @Test
        @DisplayName("Test delete_by_user_id calls delete_by_user_id on all managers")
        void testDeleteCallsAllManagers() {
            writeManager.deleteMemByUserId("user1", "scope1").join();

            verify(mockUserProfileManager, times(1)).deleteByUserId("user1", "scope1");
            verify(mockVariableManager, times(1)).deleteByUserId("user1", "scope1");
        }
    }
}



