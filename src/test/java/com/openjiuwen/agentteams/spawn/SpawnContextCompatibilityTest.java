
package com.openjiuwen.agentteams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SpawnContextCompatibilityTest {
    @AfterEach
    void cleanup() {
        SpawnContext.resetSessionId(null);
    }

    @Test
    void contextShouldDefaultToEmptyAndRestorePreviousSession() {
        assertThat(SpawnContext.getSessionId()).isEmpty();

        SpawnContext.SessionToken first = SpawnContext.setSessionId("session-a");
        assertThat(SpawnContext.getSessionId()).isEqualTo("session-a");

        SpawnContext.SessionToken second = SpawnContext.setSessionId("session-b");
        assertThat(SpawnContext.getSessionId()).isEqualTo("session-b");

        SpawnContext.resetSessionId(second);
        assertThat(SpawnContext.getSessionId()).isEqualTo("session-a");

        SpawnContext.resetSessionId(first);
        assertThat(SpawnContext.getSessionId()).isEmpty();
    }
}
