/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextEvolverConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void resetConfig() {
        Config.restore(Map.of());
    }

    @Test
    void loadPrefersDotEnvAndConvertsScalarTypes() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(
                envFile,
                "BOOL=true\nINT_VALUE=7\nFLOAT_VALUE=3.5\nLOCAL_ONLY=from_env\n",
                StandardCharsets.UTF_8
        );
        Path yamlFile = tempDir.resolve("config.yaml");
        Files.writeString(
                yamlFile,
                "BOOL: false\nYAML_ONLY: yaml_value\nFLOAT_VALUE: 9.25\n",
                StandardCharsets.UTF_8
        );

        Config.restore(Map.of());
        Config.load(yamlFile.toString(), envFile.toString());

        assertEquals(Boolean.TRUE, Config.get("BOOL"));
        assertEquals(7, Config.get("INT_VALUE"));
        assertEquals(3.5d, ((Number) Config.get("FLOAT_VALUE")).doubleValue(), 0.0001d);
        assertEquals("from_env", Config.get("LOCAL_ONLY"));
        assertEquals("yaml_value", Config.get("YAML_ONLY"));
        assertEquals("fallback", Config.get("MISSING", "fallback"));
    }

    @Test
    void setDeleteSnapshotAndRestoreMirrorPythonHelpers() {
        Config.restore(Map.of());
        Config.setValue("FLAG", true);
        Config.setValue("COUNT", 2);
        Map<String, Object> snapshot = Config.snapshot();

        assertEquals(Boolean.TRUE, Config.get("FLAG"));
        assertEquals(2, Config.get("COUNT"));

        Config.delete("FLAG");
        assertFalse((Boolean) Config.get("FLAG", false));

        Config.restore(snapshot);
        assertTrue((Boolean) Config.get("FLAG"));
        assertEquals(2, Config.get("COUNT"));
    }
}
