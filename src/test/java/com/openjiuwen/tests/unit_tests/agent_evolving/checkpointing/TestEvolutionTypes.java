/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.checkpointing.CheckpointTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EvolutionTypes.
 * <p>
 * Mirrors Python's {@code test_evolution_types.py} in
 * {@code tests/unit_tests/agent_evolving/checkpointing/}.
 */
@DisplayName("EvolutionTypes Tests")
class TestEvolutionTypes {

    @Test
    @DisplayName("valid sections contains original sections")
    void testValidSectionsContainsOriginalSections() {
        assertThat(CheckpointTypes.VALID_SECTIONS)
            .contains("Instructions", "Examples", "Troubleshooting", "Scripts");
    }

    @Test
    @DisplayName("valid sections contains collaboration")
    void testValidSectionsContainsCollaboration() {
        assertThat(CheckpointTypes.VALID_SECTIONS).contains("Collaboration");
    }

    @Test
    @DisplayName("valid sections contains roles")
    void testValidSectionsContainsRoles() {
        assertThat(CheckpointTypes.VALID_SECTIONS).contains("Roles");
    }

    @Test
    @DisplayName("valid sections contains constraints")
    void testValidSectionsContainsConstraints() {
        assertThat(CheckpointTypes.VALID_SECTIONS).contains("Constraints");
    }

    @Test
    @DisplayName("valid sections total count")
    void testValidSectionsTotalCount() {
        assertThat(CheckpointTypes.VALID_SECTIONS).hasSize(7);
    }
}
