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

    private static final Path REACT_AGENT_SOURCE = Path.of(
            "src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java"
    );

    @Test
    void reactAgentShouldRouteInvokeAndStreamThroughRunSharedLoop() throws IOException {
        String source = readReActAgentSource();
        int invokeStart = source.indexOf("public Object invoke(");
        int streamStart = source.indexOf("public Iterator<Object> stream(");

        assertThat(source)
                .contains("public Object invoke(")
                .contains("public Iterator<Object> stream(");
        assertThat(invokeStart).isNotNegative();
        assertThat(streamStart).isGreaterThan(invokeStart);
        assertThat(source.substring(invokeStart, streamStart)).contains("runSharedLoop(");
        assertThat(source.substring(streamStart)).contains("runSharedLoop(");
    }

    @Test
    void reactAgentShouldNotKeepLegacyIndependentStreamLoopHelper() throws IOException {
        String source = readReActAgentSource();

        assertThat(source).doesNotContain("private void runStreamLoop(");
    }

    @Test
    void reactAgentShouldModelFormalTerminalOutcomesInOneSharedSource() throws IOException {
        String source = readReActAgentSource();

        assertThat(source).contains("TerminalOutcome");
        assertThat(source).contains("SUCCESS");
        assertThat(source).contains("FAILURE");
        assertThat(source).contains("INTERRUPT_PENDING");
        assertThat(source).doesNotContain("writeCompletedAnswerFrame(");
        assertThat(source).doesNotContain("writeFailedFinal(");
    }

    private static String readReActAgentSource() throws IOException {
        return Files.readString(resolveModuleRoot().resolve(REACT_AGENT_SOURCE));
    }

    private static Path resolveModuleRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(REACT_AGENT_SOURCE))) {
                return current;
            }
            Path moduleRoot = current.resolve("agent-core-java");
            if (Files.exists(moduleRoot.resolve(REACT_AGENT_SOURCE))) {
                return moduleRoot;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate ReActAgent.java from user.dir");
    }
}
