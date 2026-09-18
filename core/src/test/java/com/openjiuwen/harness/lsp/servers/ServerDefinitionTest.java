/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerDefinitionTest {

    @Test
    void defaultsAndCallbacksMirrorPythonDataclassSurface() {
        ServerDefinition definition = new ServerDefinition();
        definition.setId("pyright");
        definition.setExtensions(List.of(".py"));
        definition.setLanguageId("python");
        definition.setFindRoot(path -> "/repo");
        definition.setSpawn(path -> {
            SpawnHandle handle = new SpawnHandle();
            handle.setCommand("pyright-langserver");
            return handle;
        });

        assertThat(definition.getPriority()).isEqualTo(100);
        assertThat(definition.isGlobalServer()).isFalse();
        assertThat(definition.getFindRoot().apply("a.py")).isEqualTo("/repo");
        assertThat(definition.getSpawn().apply("/repo").getCommand()).isEqualTo("pyright-langserver");
    }
}
