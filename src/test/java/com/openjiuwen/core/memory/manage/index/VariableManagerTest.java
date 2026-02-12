/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.foundation.store.InMemoryKVStore;
import com.openjiuwen.core.memory.manage.memmodel.VariableUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VariableManager.
 * Converted from Python: test_variable_manager.py
 */
class VariableManagerTest {

    private InMemoryKVStore kvStore;
    private byte[] cryptoKey;
    private byte[] emptyCryptoKey;
    private VariableManager variableManager;
    private VariableManager encryptedVariableManager;

    @BeforeEach
    void setUp() {
        kvStore = new InMemoryKVStore();
        cryptoKey = "1234567890abcdef1234567890abcdef".getBytes();
        emptyCryptoKey = new byte[0];
        variableManager = new VariableManager(kvStore, emptyCryptoKey);
        encryptedVariableManager = new VariableManager(kvStore, cryptoKey);
    }

    @Nested
    @DisplayName("Add Tests")
    class AddTests {

        @Test
        @DisplayName("Test successful variable addition")
        void testAddSuccess() {
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("favorite_food")
                .variableMem("川菜")
                .build();

            variableManager.add(variable, null).join();

            // Verify stored
            String key = "user_var/user1/scope1/favorite_food";
            String result = kvStore.get(key).join();
            assertEquals("川菜", result);
        }

        @Test
        @DisplayName("Test variable is encrypted when crypto_key is set")
        void testAddWithEncryption() {
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("secret")
                .variableMem("sensitive_data")
                .build();

            encryptedVariableManager.add(variable, null).join();

            // Verify stored value is encrypted (not plaintext)
            String key = "user_var/user1/scope1/secret";
            String result = kvStore.get(key).join();
            assertNotEquals("sensitive_data", result);
            assertTrue(result.length() > "sensitive_data".length());
        }

        @Test
        @DisplayName("Test add with None kv_store logs error and returns")
        void testAddNoneKvStoreLogsError() {
            VariableManager manager = new VariableManager(null, emptyCryptoKey);
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("test")
                .variableMem("value")
                .build();

            // Should not raise, just log error
            assertDoesNotThrow(() -> manager.add(variable, null).join());
        }
    }

    @Nested
    @DisplayName("Update User Variable Tests")
    class UpdateUserVariableTests {

        @Test
        @DisplayName("Test updating an existing variable")
        void testUpdateExistingVariable() {
            // First add a variable
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("age")
                .variableMem("25")
                .build();
            variableManager.add(variable, null).join();

            // Update it
            variableManager.updateUserVariable("user1", "scope1", "age", "26").join();

            // Verify updated
            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "age", null).join();
            assertEquals("26", result.get("age"));
        }

        @Test
        @DisplayName("Test updating a nonexistent variable does nothing")
        void testUpdateNonexistentVariableDoesNothing() {
            variableManager.updateUserVariable("user1", "scope1", "nonexistent", "value").join();

            // Variable should not exist
            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "nonexistent", null).join();
            assertNull(result.get("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Delete User Variable Tests")
    class DeleteUserVariableTests {

        @Test
        @DisplayName("Test deleting an existing variable")
        void testDeleteExistingVariable() {
            // Add a variable
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("to_delete")
                .variableMem("value")
                .build();
            variableManager.add(variable, null).join();

            // Delete it
            variableManager.deleteUserVariable("user1", "scope1", "to_delete").join();

            // Verify deleted
            String key = "user_var/user1/scope1/to_delete";
            String result = kvStore.get(key).join();
            assertNull(result);
        }

        @Test
        @DisplayName("Test delete with None kv_store logs error")
        void testDeleteWithNoneKvStore() {
            VariableManager manager = new VariableManager(null, emptyCryptoKey);

            // Should not raise
            assertDoesNotThrow(() -> manager.deleteUserVariable("user1", "scope1", "test").join());
        }
    }

    @Nested
    @DisplayName("Delete By User ID Tests")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("Test deleting all variables for a user")
        void testDeleteAllUserVariables() {
            // Add multiple variables
            String[] names = {"var1", "var2", "var3"};
            for (String name : names) {
                VariableUnit variable = VariableUnit.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .variableName(name)
                    .variableMem("value_" + name)
                    .build();
                variableManager.add(variable, null).join();
            }

            // Delete all by user_id
            variableManager.deleteByUserId("user1", "scope1").join();

            // Verify all deleted
            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", null, null).join();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Test delete_by_user_id doesn't affect other users")
        void testDeleteDoesNotAffectOtherUsers() {
            // Add variables for two users
            String[] userIds = {"user1", "user2"};
            for (String userId : userIds) {
                VariableUnit variable = VariableUnit.builder()
                    .userId(userId)
                    .scopeId("scope1")
                    .variableName("shared_name")
                    .variableMem("value_" + userId)
                    .build();
                variableManager.add(variable, null).join();
            }

            // Delete user1's variables
            variableManager.deleteByUserId("user1", "scope1").join();

            // user2's variable should still exist
            Map<String, Object> result = variableManager.queryVariable("user2", "scope1", "shared_name", null).join();
            assertEquals("value_user2", result.get("shared_name"));
        }
    }

    @Nested
    @DisplayName("Query Variable Tests")
    class QueryVariableTests {

        @Test
        @DisplayName("Test querying all variables for a user (name=null)")
        void testQueryAllVariables() {
            // Add multiple variables
            String[] names = {"var1", "var2"};
            for (String name : names) {
                VariableUnit variable = VariableUnit.builder()
                    .userId("user1")
                    .scopeId("scope1")
                    .variableName(name)
                    .variableMem("value_" + name)
                    .build();
                variableManager.add(variable, null).join();
            }

            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", null, null).join();

            assertEquals(2, result.size());
            assertEquals("value_var1", result.get("var1"));
            assertEquals("value_var2", result.get("var2"));
        }

        @Test
        @DisplayName("Test querying a single variable by name")
        void testQuerySingleVariable() {
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("target")
                .variableMem("target_value")
                .build();
            variableManager.add(variable, null).join();

            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "target", null).join();

            assertEquals(Map.of("target", "target_value"), result);
        }

        @Test
        @DisplayName("Test querying a nonexistent variable returns null value")
        void testQueryNonexistentVariable() {
            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "nonexistent", null).join();

            assertEquals(1, result.size());
            assertNull(result.get("nonexistent"));
        }

        @Test
        @DisplayName("Test querying decrypts the stored value")
        void testQueryWithEncryption() {
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("secret")
                .variableMem("decrypted_value")
                .build();
            encryptedVariableManager.add(variable, null).join();

            Map<String, Object> result = encryptedVariableManager.queryVariable("user1", "scope1", "secret", null).join();

            assertEquals("decrypted_value", result.get("secret"));
        }

        @Test
        @DisplayName("Test query with empty string name returns all variables")
        void testQueryEmptyNameReturnsAll() {
            VariableUnit variable = VariableUnit.builder()
                .userId("user1")
                .scopeId("scope1")
                .variableName("test")
                .variableMem("value")
                .build();
            variableManager.add(variable, null).join();

            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "", null).join();

            assertTrue(result.containsKey("test"));
        }
    }

    @Nested
    @DisplayName("Session Variable Tests")
    class SessionVariableTests {

        @Test
        @DisplayName("Test querying a session-level variable")
        void testQuerySessionVariable() {
            // Manually set a session variable
            String sessionKey = "session_var/user1/scope1/session123/temp_var";
            kvStore.set(sessionKey, "session_value").join();

            Map<String, Object> result = variableManager.queryVariable("user1", "scope1", "temp_var", "session123").join();

            assertEquals(Map.of("temp_var", "session_value"), result);
        }
    }
}



