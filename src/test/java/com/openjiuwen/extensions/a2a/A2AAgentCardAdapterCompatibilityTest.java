package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2AAgentCardAdapterCompatibilityTest {

    @Test
    void toA2ACardShouldMapDescriptionAndDefaultModes() {
        AgentCard card = AgentCard.builder()
                .id("agent-1")
                .name("demo-agent")
                .description("Demo agent")
                .inputParams(Map.of("query", Map.of("type", "string")))
                .outputParams(Map.of("answer", Map.of("type", "string")))
                .build();

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(card);

        assertThat(result.getName()).isEqualTo("demo-agent");
        assertThat(String.valueOf(result.getDescription())).contains("[input_params]");
        assertThat(String.valueOf(result.getDescription())).contains("[output_params]");
        assertThat(result.getDefaultInputModes()).isEqualTo(List.of("text/plain", "application/json"));
        assertThat(result.getDefaultOutputModes()).isEqualTo(List.of("text/plain", "application/json"));
    }

    @Test
    void toA2ACardShouldPreferExplicitSupportedInterfaces() {
        AgentCard card = AgentCard.builder().name("demo").description("desc").build();

        A2AAgentCardAdapter.A2aAgentCard result = A2AAgentCardAdapter.toA2aAgentCard(
                card,
                "https://ignored.example.com/a2a/jsonrpc",
                "HTTP+JSON",
                "1.0",
                null,
                List.of(Map.of(
                        "url", "https://grpc.example.com/a2a",
                        "protocol_binding", "GRPC",
                        "protocol_version", "1.0"
                ))
        );

        List<A2AAgentCardAdapter.AgentInterface> interfaces = result.getSupportedInterfaces();
        assertThat(interfaces).hasSize(1);
        assertThat(interfaces.get(0).getUrl()).isEqualTo("https://grpc.example.com/a2a");
        assertThat(interfaces.get(0).getProtocolBinding()).isEqualTo("GRPC");
    }
}
