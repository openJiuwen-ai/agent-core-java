/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_file_connector.py}
 * JSON file connector coverage.
 */
class JsonFileConnectorTest {

    @TempDir
    Path tempDir;

    @Test
    void basicSaveLoadExistsAndDeleteMirrorPythonConnector() {
        JSONFileConnector connector = new JSONFileConnector();
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("key1", "value1");
        testData.put("key2", List.of(1, 2, 3));
        testData.put("key3", Map.of("nested", "data"));

        String targetFile = tempDir.resolve("test_output.json").toString();
        connector.saveToFile(targetFile, testData);

        assertTrue(connector.exists(targetFile));
        assertEquals(testData, connector.loadFromFile(targetFile));
        assertTrue(connector.delete(targetFile));
        assertFalse(connector.exists(targetFile));
    }

    @Test
    void unicodeContentIsPreservedThroughRoundTrip() {
        JSONFileConnector connector = new JSONFileConnector();
        Map<String, Object> testData = Map.of(
                "chinese", "测试",
                "emoji", "\uD83C\uDF80",
                "japanese", "テスト"
        );

        String targetFile = tempDir.resolve("unicode_test.json").toString();
        connector.saveToFile(targetFile, testData);

        assertEquals(testData, connector.loadFromFile(targetFile));
    }

    @Test
    void safeModelDumpFallsBackAcrossPythonStyleSerializationMethods() {
        assertEquals(Map.of("kind", "model"), JSONFileConnector.safeModelDump(new WithModelDump()));
        assertEquals(Map.of("kind", "dict"), JSONFileConnector.safeModelDump(new WithToDict()));
        assertEquals(Map.of("kind", "legacy"), JSONFileConnector.safeModelDump(new WithDict()));
    }

    private static final class WithModelDump {
        public Map<String, Object> modelDump() {
            return Map.of("kind", "model");
        }
    }

    private static final class WithToDict {
        public Map<String, Object> toDict() {
            return Map.of("kind", "dict");
        }
    }

    private static final class WithDict {
        public Map<String, Object> dict() {
            return Map.of("kind", "legacy");
        }
    }
}
