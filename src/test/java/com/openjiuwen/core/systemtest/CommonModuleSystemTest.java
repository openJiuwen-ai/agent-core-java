/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.common.utils.HashUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Common module.
 * Tests constants, exception handling, utilities, and schema classes.
 */
@Tag("system-test")
class CommonModuleSystemTest {

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("ControllerType enum values")
        void testControllerType() {
            assertNotNull(ControllerType.valueOf("REACT_CONTROLLER"));
            assertNotNull(ControllerType.valueOf("WORKFLOW_CONTROLLER"));
            System.out.println("[Constants] ControllerTypes: "
                    + java.util.Arrays.toString(ControllerType.values()));
        }

        @Test
        @DisplayName("TaskType enum values")
        void testTaskType() {
            assertNotNull(TaskType.valueOf("PLUGIN"));
            assertNotNull(TaskType.valueOf("WORKFLOW"));
            assertNotNull(TaskType.valueOf("MCP"));
            System.out.println("[Constants] TaskTypes: "
                    + java.util.Arrays.toString(TaskType.values()));
        }
    }

    @Nested
    @DisplayName("JSON Utilities Tests")
    class JsonUtilsTests {

        @Test
        @DisplayName("JsonUtils serialize and deserialize map")
        void testJsonSerializeDeserialize() {
            Map<String, Object> original = Map.of(
                    "name", "test",
                    "value", 42,
                    "nested", Map.of("key", "value"));

            String json = JsonUtils.safeJsonDumps(original);
            assertNotNull(json);
            assertFalse(json.isEmpty());

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JsonUtils.safeJsonLoads(json, Map.class);
            assertNotNull(parsed);
            assertEquals("test", parsed.get("name"));
            assertEquals(42, parsed.get("value"));
            System.out.println("[JsonUtils] JSON: " + json);
        }

        @Test
        @DisplayName("JsonUtils safeJsonDumps with default on null")
        void testJsonSafeDefault() {
            String json = JsonUtils.safeJsonDumps(null, "{}");
            assertNotNull(json);
            System.out.println("[JsonUtils SafeDefault] Result: " + json);
        }
    }

    @Nested
    @DisplayName("DictUtils Tests")
    class DictUtilsTests {

        @Test
        @DisplayName("DictUtils createNestedMap")
        void testDictUtilsCreateNested() {
            Object result = DictUtils.createNestedMap("a.b.c", "value");
            assertNotNull(result);
            System.out.println("[DictUtils Create] " + result);
        }

        @Test
        @DisplayName("DictUtils flattenMap")
        void testDictUtilsFlatten() {
            Map<String, Object> nested = Map.of(
                    "level1", Map.of("level2", "value"));
            Map<String, Object> flat = DictUtils.flattenMap(nested);
            assertNotNull(flat);
            System.out.println("[DictUtils Flatten] " + flat);
        }
    }

    @Nested
    @DisplayName("HashUtil Tests")
    class HashUtilTests {

        @Test
        @DisplayName("HashUtil generates consistent SHA-256 keys")
        void testHashUtilConsistency() {
            String hash1 = HashUtil.generateKey("test-key", "test-base");
            String hash2 = HashUtil.generateKey("test-key", "test-base");
            assertNotNull(hash1);
            assertEquals(hash1, hash2, "Same input should produce same hash");
            System.out.println("[HashUtil] Hash: " + hash1);
        }

        @Test
        @DisplayName("HashUtil different inputs produce different hashes")
        void testHashUtilDifferentInputs() {
            String hash1 = HashUtil.generateKey("key1", "base1");
            String hash2 = HashUtil.generateKey("key2", "base2");
            assertFalse(hash1.equals(hash2),
                    "Different inputs should produce different hashes");
        }

        @Test
        @DisplayName("HashUtil with model provider")
        void testHashUtilWithProvider() {
            String hash = HashUtil.generateKey("key", "base", "OpenAI");
            assertNotNull(hash);
            System.out.println("[HashUtil Provider] Hash: " + hash);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorTests {

        @Test
        @DisplayName("ErrorHelper builds error with status code")
        void testErrorHelperBuild() {
            try {
                BaseError error = ErrorHelper.buildError(StatusCode.ERROR);
                assertNotNull(error);
                assertNotNull(error.getMessage());
                System.out.println("[ErrorHelper Build] Error: " + error.getMessage());
            } catch (Exception e) {
                System.out.println("[ErrorHelper Build] Exception: " + e.getClass().getSimpleName()
                        + " - " + e.getMessage());
            }
        }

        @Test
        @DisplayName("ErrorHelper raiseError throws correctly")
        void testErrorHelperRaise() {
            try {
                ErrorHelper.raiseError(StatusCode.ERROR);
                assertTrue(false, "Should have thrown exception");
            } catch (BaseError e) {
                assertNotNull(e.getMessage());
                System.out.println("[ErrorHelper Raise] Error: " + e.getMessage());
            }
        }
    }
}
