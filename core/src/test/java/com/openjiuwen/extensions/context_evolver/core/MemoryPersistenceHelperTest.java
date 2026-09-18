/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryPersistenceHelperTest {

    @TempDir
    private Path tempDir;

    @Test
    void jsonPathExpandsUserAndAlgorithmPlaceholders() {
        MemoryPersistenceHelper helper = jsonHelper();

        assertThat(helper.jsonPath("user-1", "ace"))
                .isEqualTo("ace/user-1.json");
    }

    @Test
    void saveJsonMergesExistingDataAndLoadReturnsEmptyForMissingFile() {
        MemoryPersistenceHelper helper = jsonHelper();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("node-1", Map.of("content", "first"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("node-2", Map.of("content", "second"));

        assertThat(helper.load("user-1", "ace")).isEmpty();
        helper.save("user-1", "ace", first);
        helper.save("user-1", "ace", second);

        Map<String, Object> loaded = helper.load("user-1", "ace");
        assertThat(loaded).containsKeys("node-1", "node-2");
    }

    @Test
    void saveSkipsEmptyNodes() {
        MemoryPersistenceHelper helper = jsonHelper();

        helper.save("user-1", "ace", Map.of());

        assertThat(Files.exists(tempDir.resolve("ace/user-1.json"))).isFalse();
    }

    @Test
    void unknownPersistenceTypeRaisesSameMessageShape() {
        MemoryPersistenceHelper helper = new MemoryPersistenceHelper(
                "bad",
                "{algo_name}/{user_id}.json",
                "localhost",
                19530,
                "vector_nodes",
                tempDir
        );

        assertThatThrownBy(() -> helper.load("user-1", "ace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown persist_type 'bad'. Must be 'auto', 'json', or 'milvus'.");
    }

    @Test
    void namespaceAndRepresentationMatchPythonShape() {
        MemoryPersistenceHelper helper = jsonHelper();

        assertThat(MemoryPersistenceHelper.namespace("user-1", "reme")).isEqualTo("memory_reme_user-1");
        assertThat(helper.toString())
                .isEqualTo("MemoryPersistenceHelper(persist_type='json', persist_path='"
                        + "{algo_name}/{user_id}.json"
                        + "')");
    }

    private MemoryPersistenceHelper jsonHelper() {
        return new MemoryPersistenceHelper(
                "json",
                "{algo_name}/{user_id}.json",
                "localhost",
                19530,
                "vector_nodes",
                tempDir
        );
    }
}
