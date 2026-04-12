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

        assertThat(source).contains("runSharedLoop(");
        assertThat(extractMethod(source, "public Object invoke(")).contains("prepareExecution(")
                .contains("runSharedLoop(");
        assertThat(extractMethod(source, "public Iterator<Object> stream("))
                .contains("prepareExecution(")
                .contains("runSharedLoop(");
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
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate ReActAgent.java from user.dir");
    }

    private static String extractMethod(String source, String declarationPrefix) {
        int start = source.indexOf(declarationPrefix);
        assertThat(start).isGreaterThanOrEqualTo(0);

        int bodyStart = source.indexOf('{', start);
        assertThat(bodyStart).isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }

        throw new IllegalStateException("Unable to extract method body for " + declarationPrefix);
    }
}
