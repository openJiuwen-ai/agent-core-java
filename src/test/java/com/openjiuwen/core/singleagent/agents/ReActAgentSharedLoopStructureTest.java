/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentSharedLoopStructureTest {

    @Test
    void reactAgentShouldRouteInvokeAndStreamThroughRunSharedLoop() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java"));

        assertThat(source).contains("runSharedLoop(");
        assertThat(extractMethod(source, "public Object invoke(Object inputs, Session session)")).contains("prepareExecution(")
                .contains("runSharedLoop(");
        assertThat(extractMethod(source, "public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)"))
                .contains("prepareExecution(")
                .contains("runSharedLoop(");
    }

    @Test
    void reactAgentShouldNotKeepLegacyIndependentStreamLoopHelper() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java"));

        assertThat(source).doesNotContain("private void runStreamLoop(");
    }

    private static String extractMethod(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).isGreaterThanOrEqualTo(0);

        int nextMethod = source.indexOf("\n    private ", start + signature.length());
        int nextOverride = source.indexOf("\n    @Override", start + signature.length());
        int end = source.length();
        if (nextMethod >= 0) {
            end = Math.min(end, nextMethod);
        }
        if (nextOverride >= 0) {
            end = Math.min(end, nextOverride);
        }
        return source.substring(start, end);
    }
}
