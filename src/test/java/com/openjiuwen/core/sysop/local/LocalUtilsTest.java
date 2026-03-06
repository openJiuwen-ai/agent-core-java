/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for local utility classes.
 */
class LocalUtilsTest {

    @Nested
    @DisplayName("StreamEventType")
    class StreamEventTypeTests {
        @Test
        @DisplayName("values have correct string representation")
        void testValues() {
            assertEquals("stdout", StreamEventType.STDOUT.getValue());
            assertEquals("stderr", StreamEventType.STDERR.getValue());
            assertEquals("exit", StreamEventType.EXIT.getValue());
            assertEquals("error", StreamEventType.ERROR.getValue());
        }
    }

    @Nested
    @DisplayName("StreamEvent")
    class StreamEventTests {
        @Test
        @DisplayName("builder creates event with correct fields")
        void testBuilder() {
            StreamEvent event = StreamEvent.builder()
                    .type(StreamEventType.STDOUT)
                    .data("hello world")
                    .build();
            assertEquals(StreamEventType.STDOUT, event.getType());
            assertEquals("hello world", event.getData());
            assertNotNull(event.getTimestamp());
        }
    }

    @Nested
    @DisplayName("InvokeData")
    class InvokeDataTests {
        @Test
        @DisplayName("builder creates data with correct fields")
        void testBuilder() {
            InvokeData data = InvokeData.builder()
                    .stdout("output")
                    .stderr("")
                    .exitCode(0)
                    .build();
            assertEquals("output", data.getStdout());
            assertEquals("", data.getStderr());
            assertEquals(0, data.getExitCode());
            assertNull(data.getException());
        }
    }

    @Nested
    @DisplayName("OperationUtils")
    class OperationUtilsTests {
        @Test
        @DisplayName("createTmpFile creates and returns temp file path")
        void testCreateTmpFile() {
            String path = OperationUtils.createTmpFile("test content", ".txt");
            assertNotNull(path);
            assertTrue(path.endsWith(".txt"));
            // Cleanup
            OperationUtils.deleteTmpFile(path);
        }

        @Test
        @DisplayName("deleteTmpFile deletes existing file")
        void testDeleteTmpFile() {
            String path = OperationUtils.createTmpFile("to delete", ".tmp");
            assertNotNull(path);
            assertTrue(OperationUtils.deleteTmpFile(path));
        }

        @Test
        @DisplayName("deleteTmpFile returns false for non-existent file")
        void testDeleteNonExistent() {
            assertFalse(OperationUtils.deleteTmpFile("/non/existent/file.tmp"));
        }

        @Test
        @DisplayName("prepareEnvironment returns system env when custom is null")
        void testPrepareEnvironmentNull() {
            Map<String, String> env = OperationUtils.prepareEnvironment(null);
            assertNotNull(env);
            assertFalse(env.isEmpty());
        }

        @Test
        @DisplayName("prepareEnvironment merges custom env")
        void testPrepareEnvironmentCustom() {
            Map<String, String> custom = Map.of("MY_VAR", "my_value");
            Map<String, String> env = OperationUtils.prepareEnvironment(custom);
            assertEquals("my_value", env.get("MY_VAR"));
        }
    }
}
