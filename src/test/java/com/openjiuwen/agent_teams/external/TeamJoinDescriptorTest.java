/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.exception.BaseError;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the external-agent join descriptor.
 *
 * <p>Mirrors Python's {@code test_descriptor.py} for
 * {@code openjiuwen/agent_teams/external/descriptor.py}.</p>
 *
 * <p>Also mirrors Python's descriptor tests in
 * {@code tests/unit_tests/agent_teams/external/test_descriptor.py}.</p>
 */
class TeamJoinDescriptorTest {

    @Test
    void descriptorJsonRoundtripPreservesFields() {
        TeamJoinDescriptor descriptor = new TeamJoinDescriptor(
                "s1",
                "t1",
                "dev-1",
                "leader",
                "en",
                Map.of("db_type", "sqlite", "connection_string", "/tmp/team.db"),
                null
        );

        TeamJoinDescriptor restored = TeamJoinDescriptor.fromJson(descriptor.toJson());

        assertThat(restored.getSessionId()).isEqualTo("s1");
        assertThat(restored.getTeamName()).isEqualTo("t1");
        assertThat(restored.getMemberName()).isEqualTo("dev-1");
        assertThat(restored.getRole()).isEqualTo("leader");
        assertThat(restored.getLanguage()).isEqualTo("en");
        assertThat(restored.getDbConfig()).containsEntry("connection_string", "/tmp/team.db");
    }

    @Test
    void descriptorEnvRoundtrip() {
        TeamJoinDescriptor descriptor = new TeamJoinDescriptor("s", "t", "m", null, null, null, null);

        Map<String, String> env = descriptor.toEnv();
        TeamJoinDescriptor restored = TeamJoinDescriptor.fromEnv(env);

        assertThat(env).containsKey(TeamJoinDescriptor.TEAM_JOIN_ENV);
        assertThat(restored.getMemberName()).isEqualTo("m");
        assertThat(restored.getRole()).isEqualTo("teammate");
    }

    @Test
    void descriptorFromEnvMissingVarRaises() {
        assertThrows(BaseError.class, () -> TeamJoinDescriptor.fromEnv(Map.of()));
    }

    @Test
    void descriptorFromJsonMalformedRaises() {
        assertThrows(BaseError.class, () -> TeamJoinDescriptor.fromJson("{ not valid json"));
    }
}
