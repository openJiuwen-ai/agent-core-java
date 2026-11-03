
package com.openjiuwen.core.runner.drunner.server_adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class AgentAdapterCompatibilityTest {
    @Test
    void agentAdapterShouldBuildTopicFromAgentIdAndVersion() throws Exception {
        AgentAdapter adapter = new AgentAdapter("agent-1", "v1", null);

        assertThat(readField(adapter, "agentId")).isEqualTo("agent-1");
        assertThat(readField(adapter, "version")).isEqualTo("v1");
        assertThat(String.valueOf(readField(adapter, "topic"))).contains("agent-1");
    }

    @Test
    void agentAdapterShouldOwnMqServerAdapter() throws Exception {
        AgentAdapter adapter = new AgentAdapter("agent-2");

        Object server = readField(adapter, "server");

        assertThat(server).isInstanceOf(MqServerAdapter.class);
        assertThat(adapter.getServer()).isInstanceOf(MqServerAdapter.class);
        assertThat(adapter.getTopic()).contains("agent-2");
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
