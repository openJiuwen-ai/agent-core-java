
package com.openjiuwen.extensions.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class A2AAgentCardAdapterCompatibilityTest {
    @Test
    void toA2ACardShouldMapDescriptionAndDefaultModes() {
        AgentCard card = AgentCard.builder().id("agent-1").name("demo-agent").description("Demo agent")
                .inputParams(Map.of("query", Map.of("type", "string")))
                .outputParams(Map.of("answer", Map.of("type", "string"))).build();

        Map<String, Object> result = A2AAgentCardAdapter.toA2ACard(card);

        assertThat(result).containsEntry("name", "demo-agent");
        assertThat(String.valueOf(result.get("description"))).contains("[input_params]");
        assertThat(String.valueOf(result.get("description"))).contains("[output_params]");
        assertThat(result.get("defaultInputModes")).isEqualTo(List.of("text/plain", "application/json"));
        assertThat(result.get("defaultOutputModes")).isEqualTo(List.of("text/plain", "application/json"));
    }

    @Test
    void toA2ACardShouldPreferExplicitSupportedInterfaces() {
        AgentCard card = AgentCard.builder().name("demo").description("desc").build();

        Map<String, Object> result = A2AAgentCardAdapter.toA2ACard(card, "https://ignored.example.com/a2a/jsonrpc",
                "HTTP+JSON", "1.0", null, List.of(Map.of("url", "https://grpc.example.com/a2a", "protocolBinding",
                        "GRPC", "protocolVersion", "1.0")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaces = (List<Map<String, Object>>) result.get("supportedInterfaces");
        assertThat(interfaces).hasSize(1);
        assertThat(interfaces.get(0)).containsEntry("url", "https://grpc.example.com/a2a");
        assertThat(interfaces.get(0)).containsEntry("protocolBinding", "GRPC");
    }
}
