/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityRedactionTest {

    @Test
    void promptRedactionHashesWhenEnabled() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setRedactPrompts(true);

        String redacted = ObservabilityRedaction.redactPrompt("hello", config);

        assertTrue(redacted.startsWith("sha256:"));
        assertEquals("sha256:2cf24dba5fb0a30e", redacted);
    }

    @Test
    void completionRedactionTruncatesWhenDisabled() {
        ObservabilityConfig config = new ObservabilityConfig();
        config.setAttributeValueMaxLength(4);

        assertEquals("hell...<truncated 1 chars>", ObservabilityRedaction.redactCompletion("hello", config));
    }

    @Test
    void nullValuesBecomeEmptyStrings() {
        ObservabilityConfig config = new ObservabilityConfig();

        assertEquals("", ObservabilityRedaction.redactPrompt(null, config));
        assertEquals("", ObservabilityRedaction.redactCompletion(null, config));
    }
}
