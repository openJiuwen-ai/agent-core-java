/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for data container behavior.
 *
 * <p>Mirrors Python's {@code data_container} module in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
class DataContainerTest {

    @Test
    void defaultRegistrationCreatesAgentSessionContainer() {
        DataContainer container = DataContainerFactory.create();

        assertThat(DataContainerFactory.has(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE)).isTrue();
        assertThat(DataContainerFactory.listTypes()).contains(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
        assertThat(container).isInstanceOf(AgentSessionContainer.class);
    }

    @Test
    void unknownTypeMatchesPythonValueErrorMessageShape() {
        assertThatThrownBy(() -> DataContainerFactory.create("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown data_container_type: 'missing'")
                .hasMessageContaining("Available types:");
    }

    @Test
    void registerCreateAndLoadCustomContainer() {
        DataContainerFactory.register("test", new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                return new MapContainer(kwargs);
            }

            @Override
            public CompletionStage<DataContainer> load(String agentId, String sessionId, Object serialized,
                                                       Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(new MapContainer(Map.of(
                        "agent_id", agentId,
                        "session_id", sessionId,
                        "serialized", serialized
                )));
            }
        });

        DataContainer created = DataContainerFactory.create("test", Map.of("value", 3));
        DataContainer loaded = DataContainerFactory.load("test", "agent", "session", "payload")
                .toCompletableFuture()
                .join();

        assertThat(created.get(null)).isEqualTo(Map.of("value", 3));
        assertThat(loaded.get("agent_id")).isEqualTo("agent");
        assertThat(loaded.get("session_id")).isEqualTo("session");
        assertThat(loaded.get("serialized")).isEqualTo("payload");
    }

    @Test
    void agentSessionContainerDelegatesToSessionState() {
        AgentSession session = AgentSession.createAgentSession(
                "session-1",
                null,
                new AgentCard("agent-1", "Agent", "")
        );
        AgentSessionContainer container = new AgentSessionContainer(session);

        assertThat(container.update(Map.of("answer", 42))).isTrue();

        assertThat(container.get("answer")).isEqualTo(42);
        assertThat(container.get(null)).isEqualTo(Map.of(
                "global_state", Map.of("answer", 42),
                "agent_state", Map.of(),
                "trace_state", Map.of()
        ));
        assertThat(container.dump().toCompletableFuture().join()).isEqualTo(Map.of());
    }

    @Test
    void agentSessionContainerHandlesMissingSession() {
        AgentSessionContainer container = new AgentSessionContainer();

        assertThat(container.update(Map.of("answer", 42))).isFalse();
        assertThat(container.get(null)).isNull();
    }

    @Test
    void sharingPolicyDefaultsAndCopiesFieldScopes() {
        Set<String> fields = new LinkedHashSet<>(Set.of("left", "right"));
        SharingPolicy policy = new SharingPolicy(Permission.READ, fields);
        fields.add("mutated");

        assertThat(Permission.READ.getValue()).isEqualTo(1);
        assertThat(policy.getPermission()).isEqualTo(Permission.READ);
        assertThat(policy.getFieldScopes()).containsExactlyInAnyOrder("left", "right");

        policy.setPermission(null);
        policy.setFieldScopes(null);

        assertThat(policy.getPermission()).isEqualTo(Permission.READ);
        assertThat(policy.getFieldScopes()).isNull();
    }

    private record MapContainer(Map<String, Object> values) implements DataContainer {
        @Override
        public Object get(Object key) {
            if (key == null) {
                return values;
            }
            return values.get(String.valueOf(key));
        }

        @Override
        public boolean update(Map<String, Object> data) {
            return false;
        }

        @Override
        public CompletionStage<Object> dump() {
            return CompletableFuture.completedFuture(values);
        }
    }
}
